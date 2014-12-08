package com.netflix.eureka2.server.registry;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.server.metric.WriteServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistryImpl;

/**
 * @author David Liu
 */
@Singleton
public class EurekaBridgeRegistry extends EurekaServerRegistryImpl {

    @Inject
    public EurekaBridgeRegistry(WriteServerMetricFactory metricsFactory) {
        super(metricsFactory);
    }
}
