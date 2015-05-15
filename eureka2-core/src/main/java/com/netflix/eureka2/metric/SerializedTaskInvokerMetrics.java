package com.netflix.eureka2.metric;

/**
 * @author David Liu
 */
public interface SerializedTaskInvokerMetrics {

    void incrementSchedulerTaskQueue();

    void decrementSchedulerTaskQueue();

    void incrementRunningTasks();

    void decrementRunningTasks();
}
