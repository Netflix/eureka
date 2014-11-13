package com.netflix.eureka2.server;

import com.netflix.eureka2.server.registry.EurekaServerRegistryImpl;
import com.netflix.eureka2.server.registry.EurekaServerRegistryMetrics;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author David Liu
 */
@Singleton
public class EurekaBridgeRegistry extends EurekaServerRegistryImpl {

    @Inject
    public EurekaBridgeRegistry(EurekaServerRegistryMetrics metrics) {
        super(metrics);
    }
}
