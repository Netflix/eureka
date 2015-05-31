package com.netflix.eureka.cluster;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.HttpReplicationClient.HttpResponse;

/**
 * @author Tomasz Bak
 */
class TestableReplicationTask extends ReplicationTask {

    public static final String APP_NAME = "testableReplicationTaskApp";

    private final boolean batchable;
    private final int replyStatusCode;
    private final long processingDelayMs;
    private final int networkFailuresRepeatCount;

    private volatile int triggeredNetworkFailures;

    TestableReplicationTask(boolean batchable,
                            String peerNodeName,
                            String appName,
                            String id,
                            Action action,
                            int replyStatusCode,
                            long processingDelayMs,
                            int networkFailuresRepeatCount) {
        super(peerNodeName, appName, id, action);
        this.batchable = batchable;
        this.replyStatusCode = replyStatusCode;
        this.processingDelayMs = processingDelayMs;
        this.networkFailuresRepeatCount = networkFailuresRepeatCount;
    }

    @Override
    public boolean isBatchingSupported() {
        return batchable;
    }

    @Override
    public HttpResponse<Void> execute() throws Throwable {
        if (triggeredNetworkFailures < networkFailuresRepeatCount) {
            triggeredNetworkFailures++;
            throw new IOException("simulated network failure");
        }
        if (processingDelayMs > 0) {
            Thread.sleep(processingDelayMs);
        }
        return new HttpResponse<>(replyStatusCode);
    }

    public ProcessingState awaitCompletion(long timeout, TimeUnit timeUnit) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        while (endTime > System.currentTimeMillis() && getProcessingState() == ProcessingState.Pending) {
            Thread.sleep(10);
        }
        return getProcessingState();
    }

    public static TestableReplicationTaskBuilder aNonBatchableTask() {
        return new TestableReplicationTaskBuilder(false);
    }

    public static TestableReplicationTaskBuilder aBatchableTask() {
        return new TestableReplicationTaskBuilder(true);
    }

    static class TestableReplicationTaskBuilder {

        private int autoId;

        private final boolean batchable;
        private String id;
        private int replyStatusCode = 200;
        private Action action = Action.Heartbeat;
        private long processingDelayMs;
        private int networkFailuresRepeatCount;

        TestableReplicationTaskBuilder(boolean batchable) {
            this.batchable = batchable;
        }

        public TestableReplicationTaskBuilder withId(String id) {
            this.id = id;
            return this;
        }

        public TestableReplicationTaskBuilder withReplyStatusCode(int replyStatusCode) {
            this.replyStatusCode = replyStatusCode;
            return this;
        }

        public TestableReplicationTaskBuilder withAction(Action action) {
            this.action = action;
            return this;
        }

        public TestableReplicationTaskBuilder withProcessingDelay(long time, TimeUnit timeUnit) {
            this.processingDelayMs = timeUnit.toMillis(time);
            return this;
        }

        public TestableReplicationTaskBuilder withNetworkFailures(int networkFailuresRepeatCount) {
            this.networkFailuresRepeatCount = networkFailuresRepeatCount;
            return this;
        }

        public TestableReplicationTask build() {
            return new TestableReplicationTask(
                    batchable,
                    "peerNodeName#test",
                    APP_NAME,
                    id == null ? "id#" + autoId++ : id,
                    action,
                    replyStatusCode,
                    processingDelayMs,
                    networkFailuresRepeatCount
            );
        }
    }
}
