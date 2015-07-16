package com.netflix.eureka2;

import javax.inject.Singleton;

import com.google.inject.Module;
import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.module.ServerConfigurationModule;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaDashboardConfigurationModule extends ServerConfigurationModule<EurekaDashboardConfig> {

    @Provides
    @Singleton
    public EurekaServerConfig getEurekaServerConfig(EurekaDashboardConfig rootConfig) {
        return rootConfig;
    }

    public static Module fromArchaius() {
        return fromArchaius(DEFAULT_CONFIG_PREFIX);
    }

    public static Module fromArchaius(final String prefix) {
        return new EurekaDashboardConfigurationModule() {
            @Provides
            @Singleton
            public EurekaDashboardConfig getConfiguration(ConfigProxyFactory factory) {
                return factory.newProxy(EurekaDashboardConfig.class, prefix);
            }
        };
    }

    public static Module fromConfig(final EurekaDashboardConfig config) {
        return new EurekaDashboardConfigurationModule() {
            @Provides
            @Singleton
            public EurekaDashboardConfig getConfiguration() {
                return config;
            }
        };
    }
}
