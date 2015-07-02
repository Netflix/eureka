package com.netflix.eureka2.server;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.server.config.BootstrapConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaWriteServerConfigurationModule extends AbstractModule {

    @Provides
    @Singleton
    public EurekaServerConfig getEurekaServerConfig(WriteServerConfig rootConfig) {
        return rootConfig;
    }

    @Provides
    @Singleton
    public EurekaServerTransportConfig getEurekaServerTransportConfig(WriteServerConfig rootConfig) {
        return rootConfig.getEurekaTransport();
    }

    @Provides
    @Singleton
    public EurekaRegistryConfig getEurekaRegistryConfig(WriteServerConfig rootConfig) {
        return rootConfig.getEurekaRegistry();
    }

    @Provides
    @Singleton
    public EurekaClusterDiscoveryConfig getEurekaClusterDiscovery(WriteServerConfig rootConfig) {
        return rootConfig.getEurekaClusterDiscovery();
    }

    @Provides
    @Singleton
    public BootstrapConfig getBootstrapConfig(WriteServerConfig rootConfig) {
        return rootConfig.getBootstrap();
    }

    public static Module fromArchaius(final String prefix) {
        return new EurekaWriteServerConfigurationModule() {
            @Override
            protected void configure() {
            }

            @Provides
            @Singleton
            public WriteServerConfig getConfiguration(ConfigProxyFactory factory) {
                return factory.newProxy(WriteServerConfig.class, prefix);
            }
        };
    }

    public static Module fromConfig(final WriteServerConfig config) {
        return new EurekaWriteServerConfigurationModule() {
            @Override
            protected void configure() {
                bind(WriteServerConfig.class).toInstance(config);
            }
        };
    }
}
