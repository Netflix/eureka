package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerConfigurationModules {
    public static Module fromArchaius() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(EurekaServerConfig.class).asEagerSingleton();
                bind(EurekaCommonConfig.class).to(EurekaServerConfig.class);
            }
        };
    }

    public static Module fromConfig(final EurekaServerConfig config) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(EurekaCommonConfig.class).toInstance(config);
                bind(EurekaServerConfig.class).toInstance(config);
            }
        };
    }
}
