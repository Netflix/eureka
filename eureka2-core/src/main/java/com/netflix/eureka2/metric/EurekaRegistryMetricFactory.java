package com.netflix.eureka2.metric;

import com.netflix.eureka2.metric.noop.NoOpEurekaRegistryMetricFactory;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaRegistryMetricFactory {

    public static volatile EurekaRegistryMetricFactory defaultFactory = new NoOpEurekaRegistryMetricFactory();

    public abstract EurekaRegistryMetrics getEurekaServerRegistryMetrics();

    public abstract EvictionQueueMetrics getEvictionQueueMetrics();

    public abstract SerializedTaskInvokerMetrics getRegistryTaskInvokerMetrics();

    public static EurekaRegistryMetricFactory registryMetrics() {
        return defaultFactory;
    }

    public static void setDefaultFactory(EurekaRegistryMetricFactory newFactory) {
        defaultFactory = newFactory;
    }
}