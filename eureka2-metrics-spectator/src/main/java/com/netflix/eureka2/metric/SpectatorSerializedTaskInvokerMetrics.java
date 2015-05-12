package com.netflix.eureka2.metric;


import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author David Liu
 */
public class SpectatorSerializedTaskInvokerMetrics extends SpectatorEurekaMetrics implements SerializedTaskInvokerMetrics {

    private final Counter outputSuccess;
    private final Counter outputFailure;

    public SpectatorSerializedTaskInvokerMetrics(ExtendedRegistry registry, String name) {
        super(registry, name);

        outputSuccess = newCounter("outputSuccess");
        outputFailure = newCounter("outputFailure");
    }

    @Override
    public void incrementOutputSuccess() {
        outputSuccess.increment();
    }

    @Override
    public void incrementOutputFailure() {
        outputFailure.increment();
    }
}
