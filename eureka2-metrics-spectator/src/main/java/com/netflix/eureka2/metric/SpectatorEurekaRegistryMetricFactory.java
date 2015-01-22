package com.netflix.eureka2.metric;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author David Liu
 */
@Singleton
public class SpectatorEurekaRegistryMetricFactory extends EurekaRegistryMetricFactory {

    private final SpectatorEurekaRegistryMetrics eurekaServerRegistryMetrics;
    private final SpectatorEvictionQueueMetrics evictionQueueMetrics;
    private final SpectatorSerializedTaskInvokerMetrics registryTaskInvokerMetrics;

    @Inject
    public SpectatorEurekaRegistryMetricFactory(ExtendedRegistry registry) {
        this.eurekaServerRegistryMetrics = new SpectatorEurekaRegistryMetrics(registry);
        this.evictionQueueMetrics = new SpectatorEvictionQueueMetrics(registry);
        this.registryTaskInvokerMetrics = new SpectatorSerializedTaskInvokerMetrics(registry, "registryTaskInvoker");
    }

    @Override
    public SpectatorEurekaRegistryMetrics getEurekaServerRegistryMetrics() {
        return eurekaServerRegistryMetrics;
    }

    @Override
    public SpectatorEvictionQueueMetrics getEvictionQueueMetrics() {
        return evictionQueueMetrics;
    }

    @Override
    public SpectatorSerializedTaskInvokerMetrics getRegistryTaskInvokerMetrics() {
        return registryTaskInvokerMetrics;
    }
}