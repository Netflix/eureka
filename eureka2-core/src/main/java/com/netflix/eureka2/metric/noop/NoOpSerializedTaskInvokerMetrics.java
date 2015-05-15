package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpSerializedTaskInvokerMetrics implements SerializedTaskInvokerMetrics {

    public static final NoOpSerializedTaskInvokerMetrics INSTANCE = new NoOpSerializedTaskInvokerMetrics();

    @Override
    public void incrementSchedulerTaskQueue() {
    }

    @Override
    public void decrementSchedulerTaskQueue() {
    }

    @Override
    public void incrementRunningTasks() {
    }

    @Override
    public void decrementRunningTasks() {
    }
}
