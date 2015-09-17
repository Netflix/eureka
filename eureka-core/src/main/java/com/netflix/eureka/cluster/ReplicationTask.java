package com.netflix.eureka.cluster;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.shared.transport.EurekaHttpClient.HttpResponse;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that simply tracks the time the task was created.
 */
abstract class ReplicationTask {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationTask.class);

    public enum ProcessingState {Pending, Finished, Failed, Cancelled}

    protected final long submitTime = System.currentTimeMillis();
    protected final String peerNodeName;
    protected final Action action;

    private final AtomicReference<ProcessingState> processingState = new AtomicReference<>(ProcessingState.Pending);

    ReplicationTask(String peerNodeName, Action action) {
        this.peerNodeName = peerNodeName;
        this.action = action;
    }

    public abstract String getTaskName();

    public Action getAction() {
        return action;
    }

    public long getSubmitTime() {
        return this.submitTime;
    }

    public abstract boolean isBatchingSupported();

    public abstract HttpResponse<?> execute() throws Throwable;

    public void handleSuccess() {
        processingState.compareAndSet(ProcessingState.Pending, ProcessingState.Finished);
    }

    /**
     * Returns true if failure can be handled, and possibly was taken care of by rescheduling another task.
     * Returns false, if failure is not recoverable.
     */
    public void handleFailure(int statusCode, Object responseEntity) throws Throwable {
        processingState.compareAndSet(ProcessingState.Pending, ProcessingState.Failed);
        logger.warn("The replication of task {} failed with response code {}", getTaskName(), statusCode);
    }

    /**
     * Called if a task was cancelled.
     */
    public void cancel() {
        processingState.compareAndSet(ProcessingState.Pending, ProcessingState.Cancelled);
    }

    public ProcessingState getProcessingState() {
        return processingState.get();
    }
}
