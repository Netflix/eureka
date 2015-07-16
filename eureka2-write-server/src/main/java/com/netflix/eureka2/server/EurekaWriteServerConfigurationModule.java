package com.netflix.eureka2.server;

import javax.inject.Singleton;

import com.google.inject.Module;
import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.server.config.BootstrapConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.module.ServerConfigurationModule;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaWriteServerConfigurationModule extends ServerConfigurationModule<WriteServerConfig> {

    @Provides
    @Singleton
    public EurekaServerConfig getEurekaServerConfig(WriteServerConfig rootConfig) {
        return rootConfig;
    }

    @Provides
    @Singleton
    public BootstrapConfig getBootstrapConfig(WriteServerConfig rootConfig) {
        return rootConfig.getBootstrap();
    }

    public static Module fromArchaius() {
        return fromArchaius(DEFAULT_CONFIG_PREFIX);
    }

    public static Module fromArchaius(final String prefix) {
        return new EurekaWriteServerConfigurationModule() {
            @Provides
            @Singleton
            public WriteServerConfig getConfiguration(ConfigProxyFactory factory) {
                return factory.newProxy(WriteServerConfig.class, prefix);
            }
        };
    }

    public static Module fromConfig(final WriteServerConfig config) {
        return new EurekaWriteServerConfigurationModule() {
            @Provides
            @Singleton
            public WriteServerConfig getConfiguration() {
                return config;
            }
        };
    }
}
