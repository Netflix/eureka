package com.netflix.eureka2.server.registry;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;

/**
 * @author David Liu
 */
@Singleton
public class EurekaBridgeRegistry extends SourcedEurekaRegistryImpl {

    @Inject
    public EurekaBridgeRegistry(EurekaRegistryMetricFactory metricsFactory) {
        super(metricsFactory);
    }
}
