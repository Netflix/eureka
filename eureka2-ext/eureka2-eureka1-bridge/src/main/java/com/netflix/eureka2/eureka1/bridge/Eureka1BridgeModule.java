package com.netflix.eureka2.eureka1.bridge;

import javax.inject.Singleton;

import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.eureka1.bridge.config.Eureka1BridgeConfiguration;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorBridgeServerMetricFactory;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.governator.auto.annotations.ConditionalOnProfile;

/**
 * @author Tomasz Bak
 */
@ConditionalOnProfile(ExtAbstractModule.WRITE_PROFILE)
public class Eureka1BridgeModule extends ExtAbstractModule {

    private static final String BRIDGE_CONFIG_PREFIX = "eureka2.ext.eureka1.bridge";

    @Override
    protected void configure() {
        bind(BridgeServerMetricFactory.class).to(SpectatorBridgeServerMetricFactory.class).asEagerSingleton();
        bind(Eureka1Bridge.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public Eureka1BridgeConfiguration getAwsConfiguration(ConfigProxyFactory factory) {
        return factory.newProxy(Eureka1BridgeConfiguration.class, BRIDGE_CONFIG_PREFIX);
    }
}
