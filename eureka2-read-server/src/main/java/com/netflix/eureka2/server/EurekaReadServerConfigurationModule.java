package com.netflix.eureka2.server;

import javax.inject.Singleton;

import com.google.inject.Module;
import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.module.ServerConfigurationModule;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaReadServerConfigurationModule extends ServerConfigurationModule<EurekaServerConfig> {

    public static Module fromArchaius(final String prefix) {
        return new EurekaReadServerConfigurationModule() {
            @Provides
            @Singleton
            public EurekaServerConfig getConfiguration(ConfigProxyFactory factory) {
                return factory.newProxy(EurekaServerConfig.class, prefix);
            }
        };
    }

    public static Module fromConfig(final EurekaServerConfig config) {
        return new EurekaReadServerConfigurationModule() {
            @Provides
            @Singleton
            public EurekaServerConfig getConfiguration() {
                return config;
            }
        };
    }
}
