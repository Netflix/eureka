package com.netflix.eureka2.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistryImpl;

/**
 * @author David Liu
 */
@Singleton
public class EurekaBridgeRegistry extends EurekaServerRegistryImpl {

    @Inject
    public EurekaBridgeRegistry(EurekaServerMetricFactory metricsFactory) {
        super(metricsFactory);
    }
}
