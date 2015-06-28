package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaBridgeServerConfigurationModules {
    public static Module fromArchaius() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(BridgeServerConfig.class).asEagerSingleton();
                bind(EurekaCommonConfig.class).to(BridgeServerConfig.class);
                bind(EurekaRegistryConfig.class).to(BridgeServerConfig.class);
            }
        };
    }

    public static Module fromConfig(final BridgeServerConfig config) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(EurekaRegistryConfig.class).toInstance(config);
                bind(EurekaCommonConfig.class).toInstance(config);
                bind(EurekaServerConfig.class).toInstance(config);
                bind(WriteServerConfig.class).toInstance(config);
                bind(BridgeServerConfig.class).toInstance(config);
            }
        };
    }
}
