package com.netflix.eureka2.metric;


import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author David Liu
 */
public class SpectatorSerializedTaskInvokerMetrics extends SpectatorEurekaMetrics implements SerializedTaskInvokerMetrics {

    private final AtomicInteger schedulerTaskQueue = new AtomicInteger();
    private final AtomicInteger runningTasks = new AtomicInteger();

    public SpectatorSerializedTaskInvokerMetrics(ExtendedRegistry registry, String name) {
        super(registry, name);

        newGauge("schedulerTaskQueue", schedulerTaskQueue);
        newGauge("runningTasks", runningTasks);
    }

    @Override
    public void incrementSchedulerTaskQueue() {
        schedulerTaskQueue.incrementAndGet();
    }

    @Override
    public void decrementSchedulerTaskQueue() {
        schedulerTaskQueue.decrementAndGet();
    }

    @Override
    public void incrementRunningTasks() {
        runningTasks.incrementAndGet();
    }

    @Override
    public void decrementRunningTasks() {
        runningTasks.decrementAndGet();
    }
}
