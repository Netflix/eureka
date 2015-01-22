package com.netflix.eureka2.metric;


import java.util.concurrent.Callable;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.ValueFunction;

/**
 * @author David Liu
 */
public class SpectatorSerializedTaskInvokerMetrics extends SpectatorEurekaMetrics implements SerializedTaskInvokerMetrics {

    private final Counter inputSuccess;
    private final Counter inputFailure;
    private final Counter outputSuccess;
    private final Counter outputFailure;

    public SpectatorSerializedTaskInvokerMetrics(ExtendedRegistry registry, String name) {
        super(registry, name);

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
    public void setQueueSizeMonitor(final Callable<Long> n) {
        newLongGauge("queueSize", new ValueFunction() {
            @Override
            public double apply(Object ref) {
                try {
                    return n.call();
                } catch (Exception e) {
                    throw new RuntimeException("Unexpected error", e);
                }
            }
        });
    }
}
