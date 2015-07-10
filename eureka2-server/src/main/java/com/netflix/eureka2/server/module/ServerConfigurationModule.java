package com.netflix.eureka2.server.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.archaius.bridge.StaticArchaiusBridgeModule;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

import javax.inject.Singleton;

/**
 * @author David Liu
 */
public abstract class ServerConfigurationModule<T extends EurekaServerConfig> extends AbstractModule {

    @Override
    protected void configure() {
        install(new ArchaiusModule());

        install(new StaticArchaiusBridgeModule());  // required to bridge archaius1 that is still used by adminModule
    }

    @Provides
    @Singleton
    public EurekaServerTransportConfig getEurekaServerTransportConfig(T rootConfig) {
        return rootConfig.getEurekaTransport();
    }

    @Provides
    @Singleton
    public EurekaRegistryConfig getEurekaRegistryConfig(T rootConfig) {
        return rootConfig.getEurekaRegistry();
    }

    @Provides
    @Singleton
    public EurekaClusterDiscoveryConfig getEurekaClusterDiscoveryConfig(T rootConfig) {
        return rootConfig.getEurekaClusterDiscovery();
    }

    @Provides
    @Singleton
    public EurekaInstanceInfoConfig getEurekaInstanceInfoConfig(T rootConfig) {
        return rootConfig.getEurekaInstance();
    }
}
