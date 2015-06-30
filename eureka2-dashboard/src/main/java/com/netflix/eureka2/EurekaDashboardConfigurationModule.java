package com.netflix.eureka2;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaDashboardConfigurationModule extends AbstractModule {

    private static final String PREFIX = "eureka2";

    @Provides
    @Singleton
    public EurekaServerTransportConfig getEurekaServerTransportConfig(EurekaDashboardConfig rootConfig) {
        return rootConfig.getEurekaTransport();
    }

    @Provides
    @Singleton
    public EurekaRegistryConfig getEurekaRegistryConfig(EurekaDashboardConfig rootConfig) {
        return rootConfig.getEurekaRegistry();
    }

    @Provides
    @Singleton
    public EurekaClusterDiscoveryConfig getEurekaClusterDiscovery(EurekaDashboardConfig rootConfig) {
        return rootConfig.getEurekaClusterDiscovery();
    }

    public static Module fromArchaius(final String prefix) {
        return new EurekaDashboardConfigurationModule() {
            @Override
            protected void configure() {
            }

            @Provides
            @Singleton
            public EurekaDashboardConfig getConfiguration(ConfigProxyFactory factory) {
                return factory.newProxy(EurekaDashboardConfig.class, prefix);
            }
        };
    }

    public static Module fromConfig(final EurekaDashboardConfig config) {
        return new EurekaDashboardConfigurationModule() {
            @Override
            protected void configure() {
                bind(EurekaDashboardConfig.class).toInstance(config);
            }
        };
    }
}
