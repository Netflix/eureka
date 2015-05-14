package com.netflix.eureka2.metric;

/**
 * @author David Liu
 */
public interface SerializedTaskInvokerMetrics {

    void incrementScheduledTasks();

    void decrementScheduledTasks();

    void incrementSubscribedTasks();

    void decrementSubscribedTasks();
}
