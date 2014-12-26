package com.netflix.eureka2.metric;

import javax.inject.Inject;

/**
 * @author David Liu
 */
public class EurekaRegistryMetricFactory {

    private static EurekaRegistryMetricFactory INSTANCE;

    private final EurekaRegistryMetrics eurekaServerRegistryMetrics;
    private final EvictionQueueMetrics evictionQueueMetrics;
    private final SerializedTaskInvokerMetrics registryTaskInvokerMetrics;

    @Inject
    public EurekaRegistryMetricFactory(
            EurekaRegistryMetrics eurekaServerRegistryMetrics,
            EvictionQueueMetrics evictionQueueMetrics,
            SerializedTaskInvokerMetrics registryTaskInvokerMetrics) {
        this.eurekaServerRegistryMetrics = eurekaServerRegistryMetrics;
        this.evictionQueueMetrics = evictionQueueMetrics;
        this.registryTaskInvokerMetrics = registryTaskInvokerMetrics;
    }

    public EurekaRegistryMetrics getEurekaServerRegistryMetrics() {
        return eurekaServerRegistryMetrics;
    }

    public EvictionQueueMetrics getEvictionQueueMetrics() {
        return evictionQueueMetrics;
    }

    public SerializedTaskInvokerMetrics getRegistryTaskInvokerMetrics() {
        return registryTaskInvokerMetrics;
    }

    public static EurekaRegistryMetricFactory registryMetrics() {
        if (INSTANCE == null) {
            synchronized (EurekaRegistryMetricFactory.class) {
                EurekaRegistryMetrics eurekaServerRegistryMetrics = new EurekaRegistryMetrics();
                eurekaServerRegistryMetrics.bindMetrics();

                EvictionQueueMetrics evictionQueueMetrics = new EvictionQueueMetrics();
                evictionQueueMetrics.bindMetrics();

                SerializedTaskInvokerMetrics registryTaskInvokerMetrics = new SerializedTaskInvokerMetrics("registry");
                registryTaskInvokerMetrics.bindMetrics();

                INSTANCE = new EurekaRegistryMetricFactory(
                        eurekaServerRegistryMetrics,
                        evictionQueueMetrics,
                        registryTaskInvokerMetrics
                );
            }
        }
        return INSTANCE;
    }
}