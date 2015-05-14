package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpSerializedTaskInvokerMetrics implements SerializedTaskInvokerMetrics {

    public static final NoOpSerializedTaskInvokerMetrics INSTANCE = new NoOpSerializedTaskInvokerMetrics();

    @Override
    public void incrementScheduledTasks() {
    }

    @Override
    public void decrementScheduledTasks() {
    }

    @Override
    public void incrementSubscribedTasks() {
    }

    @Override
    public void decrementSubscribedTasks() {
    }
}
