package com.netflix.eureka2.metric;


import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author David Liu
 */
public class SpectatorSerializedTaskInvokerMetrics extends SpectatorEurekaMetrics implements SerializedTaskInvokerMetrics {

    private final Counter scheduledTasks;
    private final Counter subscribedTasks;

    public SpectatorSerializedTaskInvokerMetrics(ExtendedRegistry registry, String name) {
        super(registry, name);

        scheduledTasks = newCounter("scheduledTasks");
        subscribedTasks = newCounter("subscribedTasks");
    }

    @Override
    public void incrementScheduledTasks() {
        scheduledTasks.increment();
    }

    @Override
    public void decrementScheduledTasks() {
        scheduledTasks.increment(-1);
    }

    @Override
    public void incrementSubscribedTasks() {
        subscribedTasks.increment();
    }

    @Override
    public void decrementSubscribedTasks() {
        subscribedTasks.increment(-1);
    }
}
