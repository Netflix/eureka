package com.netflix.eureka2.metric.noop;

import java.util.concurrent.Callable;

import com.netflix.eureka2.metric.EvictionQueueMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpEvictionQueueMetrics implements EvictionQueueMetrics {

    public static final NoOpEvictionQueueMetrics INSTANCE = new NoOpEvictionQueueMetrics();

    @Override
    public void incrementEvictionQueueAddCounter() {
    }

    @Override
    public void decrementEvictionQueueCounter() {
    }

    @Override
    public void setEvictionQueueSizeMonitor(Callable<Integer> evictionQueueSizeFun) {
    }
}
