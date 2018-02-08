package com.netflix.eureka.cluster;

import java.util.Collections;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eureka.cluster.TestableInstanceReplicationTask.ProcessingState;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.eureka.cluster.TestableInstanceReplicationTask.aReplicationTask;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ReplicationTaskProcessorTest {

    private final TestableHttpReplicationClient replicationClient = new TestableHttpReplicationClient();

    private ReplicationTaskProcessor replicationTaskProcessor;

    @Before
    public void setUp() throws Exception {
        replicationTaskProcessor = new ReplicationTaskProcessor("peerId#test", replicationClient);
    }

    @Test
    public void testNonBatchableTaskExecution() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withReplyStatusCode(200).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.Success));
    }

    @Test
    public void testNonBatchableTaskCongestionFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withReplyStatusCode(503).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.Congestion));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }

    @Test
    public void testNonBatchableTaskNetworkFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withNetworkFailures(1).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.TransientError));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }

    @Test
    public void testNonBatchableTaskPermanentFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withReplyStatusCode(406).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.PermanentError));
        assertThat(task.getProcessingState(), is(ProcessingState.Failed));
    }

    @Test
    public void testBatchableTaskListExecution() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withBatchReply(200);
        replicationClient.withNetworkStatusCode(200);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Success));
        assertThat(task.getProcessingState(), is(ProcessingState.Finished));
    }

    @Test
    public void testBatchableTaskCongestionFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withNetworkStatusCode(503);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Congestion));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }
    
    @Test
    public void testBatchableTaskNetworkReadTimeOutHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withReadtimeOut(1);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Congestion));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }


    @Test
    public void testBatchableTaskNetworkFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withNetworkError(1);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.TransientError));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }

    @Test
    public void testBatchableTaskPermanentFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();
        InstanceInfo instanceInfoFromPeer = InstanceInfoGenerator.takeOne();

        replicationClient.withNetworkStatusCode(200);
        replicationClient.withBatchReply(400);
        replicationClient.withInstanceInfo(instanceInfoFromPeer);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Success));
        assertThat(task.getProcessingState(), is(ProcessingState.Failed));
    }
}