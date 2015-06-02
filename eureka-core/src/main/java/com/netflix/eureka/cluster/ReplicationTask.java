package com.netflix.eureka.cluster;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.EurekaHttpClient.HttpResponse;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that simply tracks the time the task was created.
 */
abstract class ReplicationTask {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationTask.class);

    public enum ProcessingState {Pending, Finished, Failed, Cancelled}

    private final long submitTime = System.currentTimeMillis();
    private final String peerNodeName;
    private final String appName;
    private final String id;
    private final Action action;

    private InstanceInfo instanceInfo;
    private InstanceStatus overriddenStatus;
    private boolean replicateInstanceInfo;

    private final AtomicReference<ProcessingState> processingState = new AtomicReference<>(ProcessingState.Pending);

    ReplicationTask(String peerNodeName, String appName, String id, Action action) {
        this.peerNodeName = peerNodeName;
        this.appName = appName;
        this.id = id;
        this.action = action;
    }

    public String getTaskName() {
        return appName + '/' + id + ':' + action + '@' + peerNodeName;
    }

    public String getAppName() {
        return appName;
    }

    public String getId() {
        return id;
    }

    public Action getAction() {
        return action;
    }

    public long getSubmitTime() {
        return this.submitTime;
    }

    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    public void setInstanceInfo(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    public InstanceStatus getOverriddenStatus() {
        return overriddenStatus;
    }

    public void setOverriddenStatus(InstanceStatus overriddenStatus) {
        this.overriddenStatus = overriddenStatus;
    }

    public boolean shouldReplicateInstanceInfo() {
        return replicateInstanceInfo;
    }

    public void setReplicateInstanceInfo(boolean replicateInstanceInfo) {
        this.replicateInstanceInfo = replicateInstanceInfo;
    }

    public boolean isBatchingSupported() {
        return false;
    }

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
        Object[] args = {this.appName, this.id, this.action.name(), peerNodeName, statusCode};
        logger.warn("The replication of {}/{}/{} to peer {} failed with response code {}", args);
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

    abstract static class BatchableReplicationTask extends ReplicationTask {

        private final EurekaServerConfig config;

        BatchableReplicationTask(String peerNodeName, String appName, String id, Action action, EurekaServerConfig config) {
            super(peerNodeName, appName, id, action);
            this.config = config;
        }

        @Override
        public boolean isBatchingSupported() {
            return config.shouldBatchReplication();
        }
    }
}
