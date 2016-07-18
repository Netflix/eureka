package com.netflix.eureka.cluster;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.transport.ClusterSampleData;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.TestableHttpReplicationClient.HandledRequest;
import com.netflix.eureka.cluster.TestableHttpReplicationClient.RequestType;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * @author Tomasz Bak
 */
public class PeerEurekaNodeTest {

    private static final int BATCH_SIZE = 10;
    private static final long MAX_BATCHING_DELAY_MS = 10;

    private final PeerAwareInstanceRegistry registry = mock(PeerAwareInstanceRegistry.class);

    private final TestableHttpReplicationClient httpReplicationClient = new TestableHttpReplicationClient();

    private final InstanceInfo instanceInfo = ClusterSampleData.newInstanceInfo(1);
    private PeerEurekaNode peerEurekaNode;

    @Before
    public void setUp() throws Exception {
        httpReplicationClient.withNetworkStatusCode(200);
        httpReplicationClient.withBatchReply(200);
    }

    @After
    public void tearDown() throws Exception {
        if (peerEurekaNode != null) {
            peerEurekaNode.shutDown();
        }
    }

    @Test
    public void testRegistrationBatchReplication() throws Exception {
        createPeerEurekaNode().register(instanceInfo);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Register)));
    }

    @Test
    public void testCancelBatchReplication() throws Exception {
        createPeerEurekaNode().cancel(instanceInfo.getAppName(), instanceInfo.getId());

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Cancel)));
    }

    @Test
    public void testHeartbeatBatchReplication() throws Throwable {
        createPeerEurekaNode().heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Heartbeat)));
    }

    @Test
    public void testHeartbeatReplicationFailure() throws Throwable {
        httpReplicationClient.withNetworkStatusCode(200, 200);
        httpReplicationClient.withBatchReply(404); // Not found, to trigger registration
        createPeerEurekaNode().heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);

        // Heartbeat replied with an error
        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Heartbeat)));

        // Second, registration task is scheduled
        replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Register)));
    }

    @Test
    public void testHeartbeatWithInstanceInfoFromPeer() throws Throwable {
        InstanceInfo instanceInfoFromPeer = ClusterSampleData.newInstanceInfo(2);

        httpReplicationClient.withNetworkStatusCode(200);
        httpReplicationClient.withBatchReply(400);
        httpReplicationClient.withInstanceInfo(instanceInfoFromPeer);

        // InstanceInfo in response from peer will trigger local registry call
        createPeerEurekaNode().heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);
        expectRequestType(RequestType.Batch);

        // Check that registry has instanceInfo from peer
        verify(registry, timeout(1000).times(1)).register(instanceInfoFromPeer, true);
    }

    @Test
    public void testAsgStatusUpdate() throws Throwable {
        createPeerEurekaNode().statusUpdate(instanceInfo.getASGName(), ASGStatus.DISABLED);

        Object newAsgStatus = expectRequestType(RequestType.AsgStatusUpdate);
        assertThat(newAsgStatus, is(equalTo((Object) ASGStatus.DISABLED)));
    }

    @Test
    public void testStatusUpdateBatchReplication() throws Throwable {
        createPeerEurekaNode().statusUpdate(instanceInfo.getAppName(), instanceInfo.getId(), InstanceStatus.DOWN, instanceInfo);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.StatusUpdate)));
    }

    @Test
    public void testDeleteStatusOverrideBatchReplication() throws Throwable {
        createPeerEurekaNode().deleteStatusOverride(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.DeleteStatusOverride)));
    }

    private PeerEurekaNode createPeerEurekaNode() {
        EurekaServerConfig config = ClusterSampleData.newEurekaServerConfig();

        peerEurekaNode = new PeerEurekaNode(
                registry, "test", "http://test.host.com",
                httpReplicationClient,
                config,
                BATCH_SIZE,
                MAX_BATCHING_DELAY_MS,
                ClusterSampleData.RETRY_SLEEP_TIME_MS,
                ClusterSampleData.SERVER_UNAVAILABLE_SLEEP_TIME_MS
        );
        return peerEurekaNode;
    }

    private Object expectRequestType(RequestType requestType) throws InterruptedException {
        HandledRequest handledRequest = httpReplicationClient.nextHandledRequest(60, TimeUnit.SECONDS);
        assertThat(handledRequest, is(notNullValue()));
        assertThat(handledRequest.getRequestType(), is(equalTo(requestType)));
        return handledRequest.getData();
    }

    private ReplicationInstance expectSingleBatchRequest() throws InterruptedException {
        HandledRequest handledRequest = httpReplicationClient.nextHandledRequest(30, TimeUnit.SECONDS);
        assertThat(handledRequest, is(notNullValue()));
        assertThat(handledRequest.getRequestType(), is(equalTo(RequestType.Batch)));

        Object data = handledRequest.getData();
        assertThat(data, is(instanceOf(ReplicationList.class)));

        List<ReplicationInstance> replications = ((ReplicationList) data).getReplicationList();
        assertThat(replications.size(), is(equalTo(1)));
        return replications.get(0);
    }
}