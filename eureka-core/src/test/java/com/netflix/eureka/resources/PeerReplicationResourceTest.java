package com.netflix.eureka.resources;

import javax.ws.rs.core.Response;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.ClusterSampleData;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import org.junit.Test;

import static com.netflix.eureka.cluster.ClusterSampleData.newReplicationInstanceOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class PeerReplicationResourceTest {

    private final ApplicationResource applicationResource = mock(ApplicationResource.class);
    private final InstanceResource instanceResource = mock(InstanceResource.class);

    private final PeerReplicationResource peerReplicationResource = new PeerReplicationResource(mock(EurekaServerContext.class)) {
        @Override
        ApplicationResource createApplicationResource(ReplicationInstance instanceInfo) {
            return applicationResource;
        }

        @Override
        InstanceResource createInstanceResource(ReplicationInstance instanceInfo, ApplicationResource applicationResource) {
            return instanceResource;
        }
    };

    private final InstanceInfo instanceInfo = ClusterSampleData.newInstanceInfo(0);

    @Test
    public void testRegisterBatching() throws Exception {
        ReplicationList replicationList = new ReplicationList(newReplicationInstanceOf(Action.Register, instanceInfo));
        Response response = peerReplicationResource.batchReplication(replicationList);

        assertStatusOkReply(response);
        verify(applicationResource, times(1)).addInstance(instanceInfo, "true");
    }

    @Test
    public void testCancelBatching() throws Exception {
        when(instanceResource.cancelLease(anyString())).thenReturn(Response.ok().build());

        ReplicationList replicationList = new ReplicationList(newReplicationInstanceOf(Action.Cancel, instanceInfo));
        Response response = peerReplicationResource.batchReplication(replicationList);

        assertStatusOkReply(response);
        verify(instanceResource, times(1)).cancelLease("true");
    }

    @Test
    public void testHeartbeat() throws Exception {
        when(instanceResource.renewLease(anyString(), anyString(), anyString(), anyString())).thenReturn(Response.ok().build());

        ReplicationInstance replicationInstance = newReplicationInstanceOf(Action.Heartbeat, instanceInfo);
        Response response = peerReplicationResource.batchReplication(new ReplicationList(replicationInstance));

        assertStatusOkReply(response);
        verify(instanceResource, times(1)).renewLease(
                "true",
                replicationInstance.getOverriddenStatus(),
                instanceInfo.getStatus().name(),
                Long.toString(replicationInstance.getLastDirtyTimestamp())
        );
    }

    @Test
    public void testStatusUpdate() throws Exception {
        when(instanceResource.statusUpdate(anyString(), anyString(), anyString())).thenReturn(Response.ok().build());

        ReplicationInstance replicationInstance = newReplicationInstanceOf(Action.StatusUpdate, instanceInfo);
        Response response = peerReplicationResource.batchReplication(new ReplicationList(replicationInstance));

        assertStatusOkReply(response);
        verify(instanceResource, times(1)).statusUpdate(
                replicationInstance.getStatus(),
                "true",
                Long.toString(replicationInstance.getLastDirtyTimestamp())
        );
    }

    @Test
    public void testDeleteStatusOverride() throws Exception {
        when(instanceResource.deleteStatusUpdate(anyString(), anyString(), anyString())).thenReturn(Response.ok().build());

        ReplicationInstance replicationInstance = newReplicationInstanceOf(Action.DeleteStatusOverride, instanceInfo);
        Response response = peerReplicationResource.batchReplication(new ReplicationList(replicationInstance));

        assertStatusOkReply(response);
        verify(instanceResource, times(1)).deleteStatusUpdate(
                "true",
                replicationInstance.getStatus(),
                Long.toString(replicationInstance.getLastDirtyTimestamp())
        );
    }

    private static void assertStatusOkReply(Response httpResponse) {
        ReplicationListResponse entity = (ReplicationListResponse) httpResponse.getEntity();
        assertThat(entity, is(notNullValue()));
        ReplicationInstanceResponse replicationResponse = entity.getResponseList().get(0);
        assertThat(replicationResponse.getStatusCode(), is(equalTo(200)));
    }
}