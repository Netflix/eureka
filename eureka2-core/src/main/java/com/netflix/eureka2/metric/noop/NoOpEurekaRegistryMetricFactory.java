package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.metric.EvictionQueueMetrics;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpEurekaRegistryMetricFactory extends EurekaRegistryMetricFactory {

    @Override
    public EurekaRegistryMetrics getEurekaServerRegistryMetrics() {
        return NoOpEurekaRegistryMetrics.INSTANCE;
    }

    @Override
    public EvictionQueueMetrics getEvictionQueueMetrics() {
        return NoOpEvictionQueueMetrics.INSTANCE;
    }

    @Override
    public SerializedTaskInvokerMetrics getRegistryTaskInvokerMetrics() {
        return NoOpSerializedTaskInvokerMetrics.INSTANCE;
    }
}
