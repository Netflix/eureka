package com.netflix.eureka2.server;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaReadServerConfigurationModule extends AbstractModule {

    @Provides
    @Singleton
    public EurekaServerTransportConfig getEurekaServerTransportConfig(EurekaServerConfig rootConfig) {
        return rootConfig.getEurekaTransport();
    }

    @Provides
    @Singleton
    public EurekaRegistryConfig getEurekaRegistryConfig(EurekaServerConfig rootConfig) {
        return rootConfig.getEurekaRegistry();
    }

    @Provides
    @Singleton
    public EurekaClusterDiscoveryConfig getEurekaClusterDiscoveryConfig(EurekaServerConfig rootConfig) {
        return rootConfig.getEurekaClusterDiscovery();
    }

    public static Module fromArchaius(final String prefix) {
        return new EurekaReadServerConfigurationModule() {
            @Override
            protected void configure() {
            }

            @Provides
            @Singleton
            public EurekaServerConfig getConfiguration(ConfigProxyFactory factory) {
                return factory.newProxy(EurekaServerConfig.class, prefix);
            }
        };
    }

    public static Module fromConfig(final EurekaServerConfig config) {
        return new EurekaReadServerConfigurationModule() {
            @Override
            protected void configure() {
                bind(EurekaServerConfig.class).toInstance(config);
            }
        };
    }
}
