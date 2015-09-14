package com.netflix.eureka.cluster;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.TestableHttpReplicationClient.HandledRequest;
import com.netflix.eureka.cluster.TestableHttpReplicationClient.RequestType;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.eureka.cluster.ClusterSampleData.MAX_PROCESSING_DELAY_MS;
import static com.netflix.eureka.cluster.ClusterSampleData.RETRY_SLEEP_TIME_MS;
import static com.netflix.eureka.cluster.ClusterSampleData.SERVER_UNAVAILABLE_SLEEP_TIME_MS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Tomasz Bak
 */
public class PeerEurekaNodeTest {

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
    public void testRegistrationInBatchMode() throws Exception {
        createPeerEurekaNode(true).register(instanceInfo);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Register)));
    }

    @Test
    public void testRegistrationInNonBatchMode() throws Exception {
        createPeerEurekaNode(false).register(instanceInfo);

        InstanceInfo capturedInstanceInfo = (InstanceInfo) expectRequestType(RequestType.Register);
        assertThat(capturedInstanceInfo, is(equalTo(instanceInfo)));
    }

    @Test
    public void testCancelInBatchMode() throws Exception {
        createPeerEurekaNode(true).cancel(instanceInfo.getAppName(), instanceInfo.getId());

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Cancel)));
    }

    @Test
    public void testCancelInNonBatchMode() throws Exception {
        createPeerEurekaNode(false).cancel(instanceInfo.getAppName(), instanceInfo.getId());

        String capturedId = (String) expectRequestType(RequestType.Cancel);
        assertThat(capturedId, is(equalTo(instanceInfo.getId())));
    }

    @Test
    public void testHeartbeatInBatchMode() throws Throwable {
        createPeerEurekaNode(true).heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Heartbeat)));
    }

    @Test
    public void testHeartbeatInNonBatchMode() throws Throwable {
        createPeerEurekaNode(false).heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);

        Object capturedValue = expectRequestType(RequestType.Heartbeat);
        assertThat(capturedValue, is(nullValue()));
    }

    @Test
    public void testHeartbeatFailureInBatchMode() throws Throwable {
        httpReplicationClient.withNetworkStatusCode(200, 200);
        httpReplicationClient.withBatchReply(404); // Not found, to trigger registration
        createPeerEurekaNode(true).heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);

        // Heartbeat replied with an error
        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Heartbeat)));

        // Second, registration task is scheduled
        replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.Register)));
    }

    @Test
    public void testHeartbeatFailureInNonBatchMode() throws Throwable {
        httpReplicationClient.withNetworkStatusCode(404, 200);

        // Heartbeat replied with an error
        createPeerEurekaNode(false).heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);
        expectRequestType(RequestType.Heartbeat);

        // Second, registration task is scheduled
        expectRequestType(RequestType.Register);
    }

    @Test
    public void testHeartbeatWithInstanceInfoFromPeer() throws Throwable {
        InstanceInfo instanceInfoFromPeer = ClusterSampleData.newInstanceInfo(2);

        httpReplicationClient.withNetworkStatusCode(400);
        httpReplicationClient.withInstanceInfo(instanceInfoFromPeer);

        // InstanceInfo in response from peer will trigger local registry call
        createPeerEurekaNode(false).heartbeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, null, false);
        expectRequestType(RequestType.Heartbeat);

        // Check that registry has instanceInfo from peer
        verify(registry, timeout(1000).times(1)).register(instanceInfoFromPeer, true);
    }

    @Test
    public void testAsgStatusUpdate() throws Throwable {
        createPeerEurekaNode(true).statusUpdate(instanceInfo.getASGName(), ASGStatus.DISABLED);

        Object newAsgStatus = expectRequestType(RequestType.AsgStatusUpdate);
        assertThat(newAsgStatus, is(equalTo((Object) ASGStatus.DISABLED)));
    }

    @Test
    public void testStatusUpdateInBatchMode() throws Throwable {
        createPeerEurekaNode(true).statusUpdate(instanceInfo.getAppName(), instanceInfo.getId(), InstanceStatus.DOWN, instanceInfo);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.StatusUpdate)));
    }

    @Test
    public void testStatusUpdateInNonBatchMode() throws Throwable {
        createPeerEurekaNode(false).statusUpdate(instanceInfo.getAppName(), instanceInfo.getId(), InstanceStatus.DOWN, instanceInfo);

        InstanceStatus capturedStatus = (InstanceStatus) expectRequestType(RequestType.StatusUpdate);
        assertThat(capturedStatus, is(equalTo(InstanceStatus.DOWN)));
    }

    @Test
    public void testDeleteStatusOverrideInBatchMode() throws Throwable {
        createPeerEurekaNode(true).deleteStatusOverride(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo);

        ReplicationInstance replicationInstance = expectSingleBatchRequest();
        assertThat(replicationInstance.getAction(), is(equalTo(Action.DeleteStatusOverride)));
    }

    @Test
    public void testDeleteStatusOverrideInNonBatchMode() throws Throwable {
        createPeerEurekaNode(false).deleteStatusOverride(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo);
        expectRequestType(RequestType.DeleteStatusOverride);
    }

    private PeerEurekaNode createPeerEurekaNode(boolean batchEnabled) {
        EurekaServerConfig config = ClusterSampleData.newEurekaServerConfig(batchEnabled);

        peerEurekaNode = new PeerEurekaNode(
                registry, "test", "http://test.host.com",
                httpReplicationClient,
                config,
                MAX_PROCESSING_DELAY_MS,
                RETRY_SLEEP_TIME_MS,
                SERVER_UNAVAILABLE_SLEEP_TIME_MS
        );
        return peerEurekaNode;
    }

    private Object expectRequestType(RequestType requestType) throws InterruptedException {
        HandledRequest handledRequest = httpReplicationClient.nextHandledRequest(30, TimeUnit.SECONDS);
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