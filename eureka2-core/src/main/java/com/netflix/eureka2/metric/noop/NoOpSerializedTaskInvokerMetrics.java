package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpSerializedTaskInvokerMetrics implements SerializedTaskInvokerMetrics {

    public static final NoOpSerializedTaskInvokerMetrics INSTANCE = new NoOpSerializedTaskInvokerMetrics();

    @Override
    public void incrementOutputSuccess() {
    }

    @Override
    public void incrementOutputFailure() {
    }
}
