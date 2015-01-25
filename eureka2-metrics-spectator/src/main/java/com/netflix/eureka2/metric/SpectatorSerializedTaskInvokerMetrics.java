package com.netflix.eureka2.metric;


import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author David Liu
 */
public class SpectatorSerializedTaskInvokerMetrics extends SpectatorEurekaMetrics implements SerializedTaskInvokerMetrics {

    private final AtomicInteger queueSize = new AtomicInteger();
    private final Counter inputSuccess;
    private final Counter inputFailure;
    private final Counter outputSuccess;
    private final Counter outputFailure;

    public SpectatorSerializedTaskInvokerMetrics(ExtendedRegistry registry, String name) {
        super(registry, name);

        newGauge("queueSize", queueSize);
        inputSuccess = newCounter("inputSuccess");
        inputFailure = newCounter("inputFailure");
        outputSuccess = newCounter("outputSuccess");
        outputFailure = newCounter("outputFailure");
    }

    @Override
    public void incrementInputSuccess() {
        inputSuccess.increment();
    }

    @Override
    public void incrementInputFailure() {
        inputFailure.increment();
    }

    @Override
    public void incrementOutputSuccess() {
        outputSuccess.increment();
    }

    @Override
    public void incrementOutputFailure() {
        outputFailure.increment();
    }

    @Override
    public void setQueueSize(int queueSize) {
        this.queueSize.set(queueSize);
    }
}
