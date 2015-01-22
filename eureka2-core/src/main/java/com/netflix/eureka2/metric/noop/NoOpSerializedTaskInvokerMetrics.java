package com.netflix.eureka2.metric.noop;

import java.util.concurrent.Callable;

import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpSerializedTaskInvokerMetrics implements SerializedTaskInvokerMetrics {

    public static final NoOpSerializedTaskInvokerMetrics INSTANCE = new NoOpSerializedTaskInvokerMetrics();

    @Override
    public void incrementInputSuccess() {
    }

    @Override
    public void incrementInputFailure() {
    }

    @Override
    public void incrementOutputSuccess() {
    }

    @Override
    public void incrementOutputFailure() {
    }

    @Override
    public void setQueueSizeMonitor(Callable<Long> n) {
    }
}
