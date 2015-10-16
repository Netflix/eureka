package com.netflix.eureka.cluster;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;

/**
 * @author Tomasz Bak
 */
class TestableInstanceReplicationTask extends InstanceReplicationTask {

    public static final String APP_NAME = "testableReplicationTaskApp";

    public enum ProcessingState {Pending, Finished, Failed}

    private final int replyStatusCode;
    private final int networkFailuresRepeatCount;

    private final AtomicReference<ProcessingState> processingState = new AtomicReference<>(ProcessingState.Pending);

    private volatile int triggeredNetworkFailures;

    TestableInstanceReplicationTask(String peerNodeName,
                                    String appName,
                                    String id,
                                    Action action,
                                    int replyStatusCode,
                                    int networkFailuresRepeatCount) {
        super(peerNodeName, action, appName, id);
        this.replyStatusCode = replyStatusCode;
        this.networkFailuresRepeatCount = networkFailuresRepeatCount;
    }

    @Override
    public EurekaHttpResponse<Void> execute() throws Throwable {
        if (triggeredNetworkFailures < networkFailuresRepeatCount) {
            triggeredNetworkFailures++;
            throw new IOException("simulated network failure");
        }
        return EurekaHttpResponse.status(replyStatusCode);
    }

    @Override
    public void handleSuccess() {
        processingState.compareAndSet(ProcessingState.Pending, ProcessingState.Finished);
    }

    @Override
    public void handleFailure(int statusCode, Object responseEntity) throws Throwable {
        processingState.compareAndSet(ProcessingState.Pending, ProcessingState.Failed);
    }

    public ProcessingState getProcessingState() {
        return processingState.get();
    }

    public static TestableReplicationTaskBuilder aReplicationTask() {
        return new TestableReplicationTaskBuilder();
    }

    static class TestableReplicationTaskBuilder {

        private int autoId;

        private int replyStatusCode = 200;
        private Action action = Action.Heartbeat;
        private int networkFailuresRepeatCount;

        public TestableReplicationTaskBuilder withReplyStatusCode(int replyStatusCode) {
            this.replyStatusCode = replyStatusCode;
            return this;
        }

        public TestableReplicationTaskBuilder withAction(Action action) {
            this.action = action;
            return this;
        }

        public TestableReplicationTaskBuilder withNetworkFailures(int networkFailuresRepeatCount) {
            this.networkFailuresRepeatCount = networkFailuresRepeatCount;
            return this;
        }

        public TestableInstanceReplicationTask build() {
            return new TestableInstanceReplicationTask(
                    "peerNodeName#test",
                    APP_NAME,
                    "id#" + autoId++,
                    action,
                    replyStatusCode,
                    networkFailuresRepeatCount
            );
        }
    }
}
