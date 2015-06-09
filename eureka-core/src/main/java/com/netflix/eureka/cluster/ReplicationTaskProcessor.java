package com.netflix.eureka.cluster;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.EurekaHttpClient.HttpResponse;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationInstance.ReplicationInstanceBuilder;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.util.batcher.MessageBatcher;
import com.netflix.eureka.util.batcher.MessageProcessor;
import com.netflix.servo.monitor.DynamicCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.eureka.cluster.protocol.ReplicationInstance.ReplicationInstanceBuilder.aReplicationInstance;

/**
 * @author Tomasz Bak
 */
public class ReplicationTaskProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationTaskProcessor.class);

    private final String peerId;
    private final HttpReplicationClient replicationClient;
    private final EurekaServerConfig config;
    private final long retrySleepTimeMs;
    private final long serverUnavailableSleepTime;
    private final MessageBatcher<ReplicationTask> batcher;

    ReplicationTaskProcessor(String peerId,
                             String batcherName,
                             String batchedAction,
                             HttpReplicationClient replicationClient,
                             EurekaServerConfig config,
                             long maxDelay,
                             long retrySleepTimeMs,
                             long serverUnavailableSleepTime) {
        this.peerId = peerId;
        this.replicationClient = replicationClient;
        this.config = config;
        this.retrySleepTimeMs = retrySleepTimeMs;
        this.serverUnavailableSleepTime = serverUnavailableSleepTime;
        String absoluteBatcherName = batcherName + '-' + batchedAction;

        batcher = new MessageBatcher<ReplicationTask>(
                absoluteBatcherName,
                createMessageProcessor(),
                config.getMaxElementsInPeerReplicationPool(),
                maxDelay,
                config.getMinThreadsForPeerReplication(),
                config.getMaxThreadsForPeerReplication(),
                config.getMaxIdleThreadAgeInMinutesForPeerReplication() * 60 * 1000, // minutes -> ms
                true
        );
    }

    private MessageProcessor<ReplicationTask> createMessageProcessor() {
        return new MessageProcessor<ReplicationTask>() {
            @Override
            public void process(List<ReplicationTask> tasks) {
                if (!tasks.get(0).isBatchingSupported()) {
                    executeSingle(tasks);
                } else {
                    executeBatch(tasks);
                }
            }
        };
    }

    public boolean process(ReplicationTask replicationTask) {
        boolean success = batcher.process(replicationTask);
        if (!success) {
            logger.error("Cannot find space in the replication pool for peer {}. Check the network connectivity or the traffic", peerId);
        }
        return success;
    }

    public void shutdown() {
        batcher.stop();
    }

    private void executeSingle(List<ReplicationTask> tasks) {
        for (ReplicationTask task : tasks) {
            boolean done;
            do {
                done = true;
                try {
                    if (isLate(task)) {
                        continue;
                    }
                    DynamicCounter.increment("Single_" + task.getAction().name() + "_tries");

                    HttpResponse<?> httpResponse = task.execute();
                    int statusCode = httpResponse.getStatusCode();
                    if (isSuccess(statusCode)) {
                        DynamicCounter.increment("Single_" + task.getAction().name() + "_success");
                        task.handleSuccess();
                    } else {
                        DynamicCounter.increment("Single_" + task.getAction().name() + "_failure");
                        task.handleFailure(statusCode, httpResponse.getEntity());
                    }
                } catch (Throwable e) {
                    if (isNetworkConnectException(e)) {
                        logger.error("Network level connection to peer " + peerId + " for task " + task.getTaskName() + "; retrying after delay", e);
                        try {
                            Thread.sleep(retrySleepTimeMs);
                        } catch (InterruptedException ignore) {
                        }
                        DynamicCounter.increment(task.getAction().name() + "_retries");
                        done = false;
                    } else {
                        logger.error(peerId + ": " + task.getTaskName() + "Not re-trying this exception because it does not seem to be a network exception", e);
                    }
                }
            } while (!done);
        }
    }

    private void executeBatch(List<ReplicationTask> tasks) {
        ReplicationList list = createReplicationListOf(tasks);
        if (list.getReplicationList().isEmpty()) {
            return;
        }

        Action action = list.getReplicationList().get(0).getAction();
        DynamicCounter.increment("Batch_" + action + "_tries");

        boolean done;
        do {
            done = true;
            try {
                HttpResponse<ReplicationListResponse> response = replicationClient.submitBatchUpdates(list);
                int statusCode = response.getStatusCode();
                if (!isSuccess(statusCode)) {
                    if (statusCode == 503) {
                        logger.warn("Server busy (503) HTTP status code received from the peer {}; rescheduling tasks after delay", peerId);
                        rescheduleAfterDelay(tasks);
                    } else {
                        // Unexpected error returned from the server. This should ideally never happen.
                        logger.error("Batch update failure with HTTP status code {}; discarding {} replication tasks", statusCode, tasks.size());
                    }
                    return;
                }
                DynamicCounter.increment("Batch_" + action + "_success");

                handleBatchResponse(tasks, response.getEntity().getResponseList());
            } catch (Throwable e) {
                if (isNetworkConnectException(e)) {
                    logger.error("Network level connection to peer " + peerId + "; retrying after delay", e);
                    try {
                        Thread.sleep(retrySleepTimeMs);
                    } catch (InterruptedException ignore) {
                    }
                    done = false;
                    DynamicCounter.increment("Batch_" + action + "_retries");
                } else {
                    logger.error("Not re-trying this exception because it does not seem to be a network exception", e);
                }
            }
        } while (!done);
    }

    private void handleBatchResponse(List<ReplicationTask> tasks, List<ReplicationInstanceResponse> responseList) {
        if (tasks.size() != responseList.size()) {
            // This should ideally never happen unless there is a bug in the software.
            logger.error("Batch response size different from submitted task list ({} != {}); skipping response analysis", responseList.size(), tasks.size());
            return;
        }
        for (int i = 0; i < tasks.size(); i++) {
            handleBatchResponse(tasks.get(i), responseList.get(i));
        }
    }

    private void handleBatchResponse(ReplicationTask task, ReplicationInstanceResponse response) {
        int statusCode = response.getStatusCode();
        if (isSuccess(statusCode)) {
            task.handleSuccess();
            return;
        }

        try {
            task.handleFailure(response.getStatusCode(), response.getResponseEntity());
        } catch (Throwable e) {
            logger.error("Replication task " + task.getTaskName() + " error handler failure", e);
        }
    }

    private void rescheduleAfterDelay(List<ReplicationTask> tasks) {
        try {
            Thread.sleep(serverUnavailableSleepTime);
        } catch (InterruptedException e) {
            // Ignore
        }
        for (ReplicationTask task : tasks) {
            if (!isLate(task)) {
                process(task);
            }
        }
    }

    private ReplicationList createReplicationListOf(List<ReplicationTask> tasks) {
        ReplicationList list = new ReplicationList();
        for (ReplicationTask task : tasks) {
            if (!isLate(task)) {
                list.addReplicationInstance(createReplicationInstanceOf(task));
            }
        }
        return list;
    }

    private boolean isLate(ReplicationTask task) {
        long now = System.currentTimeMillis();
        boolean late = now - task.getSubmitTime() > config.getMaxTimeForReplication();

        if (late) {
            Object[] args = {
                    task.getAppName(),
                    task.getId(),
                    task.getAction(),
                    new Date(now),
                    new Date(task.getSubmitTime())};

            DynamicCounter.increment("Replication_" + task.getAction().name() + "_expiry");
            logger.warn(
                    "Replication events older than the threshold. AppName : {}, Id: {}, Action : {}, Current Time : {}, Submit Time :{}",
                    args);

            task.cancel();
        }
        return late;
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Check if the exception is some sort of network timeout exception (ie)
     * read,connect.
     *
     * @param e
     *            The exception for which the information needs to be found.
     * @return true, if it is a network timeout, false otherwise.
     */
    private static boolean isNetworkConnectException(Throwable e) {
        do {
            if (IOException.class.isInstance(e)) {
                return true;
            }
            e = e.getCause();
        } while (e != null);
        return false;
    }

    private static ReplicationInstance createReplicationInstanceOf(ReplicationTask task) {
        ReplicationInstanceBuilder instanceBuilder = aReplicationInstance();
        instanceBuilder.withAppName(task.getAppName());
        instanceBuilder.withId(task.getId());
        InstanceInfo instanceInfo = task.getInstanceInfo();
        if (instanceInfo != null) {
            String overriddenStatus = task.getOverriddenStatus() == null ? null
                    : task.getOverriddenStatus().name();
            instanceBuilder.withOverriddenStatus(overriddenStatus);
            instanceBuilder.withLastDirtyTimestamp(instanceInfo.getLastDirtyTimestamp());
            if (task.shouldReplicateInstanceInfo()) {
                instanceBuilder.withInstanceInfo(instanceInfo);
            }
            String instanceStatus = instanceInfo.getStatus() == null ? null : instanceInfo.getStatus().name();
            instanceBuilder.withStatus(instanceStatus);
        }
        instanceBuilder.withAction(task.getAction());
        return instanceBuilder.build();
    }
}
