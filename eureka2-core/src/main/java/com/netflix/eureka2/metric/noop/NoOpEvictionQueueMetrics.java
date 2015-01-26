package com.netflix.eureka2.metric.noop;

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
    public void setEvictionQueueSize(int evictionQueueSize) {
    }
}
