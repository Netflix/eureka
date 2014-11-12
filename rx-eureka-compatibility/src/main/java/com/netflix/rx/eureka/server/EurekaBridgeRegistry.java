package com.netflix.rx.eureka.server;

import com.netflix.rx.eureka.server.registry.EurekaServerRegistryImpl;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistryMetrics;

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
