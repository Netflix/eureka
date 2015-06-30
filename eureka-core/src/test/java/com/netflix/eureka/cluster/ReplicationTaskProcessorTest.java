package com.netflix.eureka.cluster;

import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.ReplicationTask.ProcessingState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.eureka.cluster.ClusterSampleData.MAX_PROCESSING_DELAY_MS;
import static com.netflix.eureka.cluster.ClusterSampleData.REPLICATION_EXPIRY_TIME_MS;
import static com.netflix.eureka.cluster.ClusterSampleData.RETRY_SLEEP_TIME_MS;
import static com.netflix.eureka.cluster.ClusterSampleData.SERVER_UNAVAILABLE_SLEEP_TIME_MS;
import static com.netflix.eureka.cluster.TestableInstanceReplicationTask.aBatchableTask;
import static com.netflix.eureka.cluster.TestableInstanceReplicationTask.aNonBatchableTask;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ReplicationTaskProcessorTest {

    private final TestableHttpReplicationClient replicationClient = new TestableHttpReplicationClient();

    private final EurekaServerConfig config = ClusterSampleData.newEurekaServerConfig(true);

    private ReplicationTaskProcessor replicationTaskProcessor;

    @Before
    public void setUp() throws Exception {
        replicationTaskProcessor = new ReplicationTaskProcessor(
                "peerId#test",
                "batcherName#test",
                Action.Heartbeat.name(),
                replicationClient,
                config,
                MAX_PROCESSING_DELAY_MS,
                RETRY_SLEEP_TIME_MS,
                SERVER_UNAVAILABLE_SLEEP_TIME_MS
        );
    }

    @After
    public void tearDown() throws Exception {
        replicationTaskProcessor.shutdown();
    }

    @Test
    public void testNonBatchableTaskExecution() throws Exception {
        TestableInstanceReplicationTask task = aNonBatchableTask().withAction(Action.Heartbeat).withReplyStatusCode(200).build();

        boolean status = replicationTaskProcessor.process(task);

        assertThat(status, is(true));
        assertThat(task.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Finished)));
    }

    @Test
    public void testNonBatchableTaskFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aNonBatchableTask().withAction(Action.Heartbeat).withReplyStatusCode(503).build();

        boolean status = replicationTaskProcessor.process(task);

        assertThat(status, is(true));
        assertThat(task.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Failed)));
    }

    @Test
    public void testNonBatchableTaskExpiry() throws Exception {
        TestableInstanceReplicationTask longTask = aNonBatchableTask()
                .withId("longTask")
                .withAction(Action.Heartbeat)
                .withReplyStatusCode(200)
                .withProcessingDelay(5 * REPLICATION_EXPIRY_TIME_MS, TimeUnit.MILLISECONDS)
                .build();
        TestableInstanceReplicationTask secondTask =
                aNonBatchableTask().withId("secondTask").withAction(Action.Heartbeat).withReplyStatusCode(200).build();

        boolean status = replicationTaskProcessor.process(longTask) && replicationTaskProcessor.process(secondTask);

        assertThat(status, is(true));
        assertThat(longTask.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Finished)));
        assertThat(secondTask.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Cancelled)));
    }

    @Test
    public void testNonBatchableTaskRetryOnConnectionError() throws Exception {
        TestableInstanceReplicationTask task = aNonBatchableTask()
                .withAction(Action.Heartbeat).withReplyStatusCode(200).withNetworkFailures(2).build();

        boolean status = replicationTaskProcessor.process(task);

        assertThat(status, is(true));
        assertThat(task.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Finished)));
    }

    @Test
    public void testBatchableTaskListExecution() throws Exception {
        TestableInstanceReplicationTask task = aBatchableTask().build();

        replicationClient.withBatchReply(200);
        replicationClient.withNetworkStatusCode(200);
        boolean status = replicationTaskProcessor.process(task);

        assertThat(status, is(true));
        assertThat(task.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Finished)));
    }

    @Test
    public void testBatchableTaskFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aBatchableTask().build();
        InstanceInfo instanceInfoFromPeer = InstanceInfoGenerator.takeOne();

        replicationClient.withNetworkStatusCode(200);
        replicationClient.withBatchReply(400);
        replicationClient.withInstanceInfo(instanceInfoFromPeer);
        boolean status = replicationTaskProcessor.process(task);

        assertThat(status, is(true));
        assertThat(task.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Failed)));
    }

    @Test
    public void testBatchableTaskExpiry() throws Exception {
        TestableInstanceReplicationTask longTask = aBatchableTask().build();

        replicationClient.withNetworkStatusCode(200);
        replicationClient.withBatchReply(200);
        replicationClient.withProcessingDelay(5 * REPLICATION_EXPIRY_TIME_MS, TimeUnit.MILLISECONDS);
        replicationTaskProcessor.process(longTask);

        // Wait a bit, to be sure long task is picked up by the batcher
        Thread.sleep(REPLICATION_EXPIRY_TIME_MS);

        TestableInstanceReplicationTask secondTask =
                aBatchableTask().withId("secondTask").withAction(Action.Heartbeat).withReplyStatusCode(200).build();
        replicationTaskProcessor.process(secondTask);

        assertThat(longTask.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Finished)));
        assertThat(secondTask.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Cancelled)));
    }

    @Test
    public void testBatchableTaskRetryOnConnectionError() throws Exception {
        TestableInstanceReplicationTask task = aBatchableTask().withAction(Action.Heartbeat).withReplyStatusCode(200).build();

        replicationClient.withNetworkStatusCode(200);
        replicationClient.withNetworkError(2);
        replicationClient.withBatchReply(200);
        boolean status = replicationTaskProcessor.process(task);

        assertThat(status, is(true));
        assertThat(task.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Finished)));
    }

    @Test
    public void testBatchableTaskRetryOnServerBusy() throws Exception {
        TestableInstanceReplicationTask task = aBatchableTask().withAction(Action.Heartbeat).withReplyStatusCode(200).build();

        replicationClient.withNetworkStatusCode(503, 200);
        replicationClient.withBatchReply(200);

        boolean status = replicationTaskProcessor.process(task);

        assertThat(status, is(true));
        assertThat(task.awaitCompletion(30, TimeUnit.SECONDS), is(equalTo(ProcessingState.Finished)));
    }
}