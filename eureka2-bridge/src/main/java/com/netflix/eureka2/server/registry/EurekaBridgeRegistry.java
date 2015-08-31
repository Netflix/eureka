package com.netflix.eureka2.server.registry;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistryImpl;

/**
 * @author David Liu
 */
@Singleton
public class EurekaBridgeRegistry extends EurekaRegistryImpl {

    @Inject
    public EurekaBridgeRegistry(EurekaRegistryMetricFactory metricsFactory) {
        super(metricsFactory);
    }
}
