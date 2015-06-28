package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerConfigurationModules {
    public static Module fromArchaius() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(WriteServerConfig.class).asEagerSingleton();
                bind(EurekaCommonConfig.class).to(WriteServerConfig.class);
                bind(EurekaRegistryConfig.class).to(WriteServerConfig.class);
            }
        };
    }

    public static Module fromConfig(final WriteServerConfig config) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(EurekaRegistryConfig.class).toInstance(config);
                bind(EurekaCommonConfig.class).toInstance(config);
                bind(EurekaServerConfig.class).toInstance(config);
                bind(WriteServerConfig.class).toInstance(config);
            }
        };
    }
}
