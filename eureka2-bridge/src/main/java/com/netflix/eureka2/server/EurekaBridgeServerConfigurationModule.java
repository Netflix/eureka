package com.netflix.eureka2.server;

import com.google.inject.Module;
import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;

import javax.inject.Singleton;

/**
 * @author David Liu
 */
public class EurekaBridgeServerConfigurationModule extends EurekaWriteServerConfigurationModule {

    @Provides
    @Singleton
    public WriteServerConfig getWriteServerConfig(BridgeServerConfig rootConfig) {
        return rootConfig;
    }

    public static Module fromArchaius(final String prefix) {
        return new EurekaBridgeServerConfigurationModule() {
            @Provides
            @Singleton
            public BridgeServerConfig getConfiguration(ConfigProxyFactory factory) {
                return factory.newProxy(BridgeServerConfig.class, prefix);
            }
        };
    }

    public static Module fromConfig(final BridgeServerConfig config) {
        return new EurekaBridgeServerConfigurationModule() {
            @Provides
            @Singleton
            public BridgeServerConfig getConfiguration() {
                return config;
            }
        };
    }
}
