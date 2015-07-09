package com.netflix.eureka2.eureka1.rest;

import javax.inject.Singleton;

import com.google.inject.Provides;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.eureka1.rest.config.Eureka1Configuration;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxy;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxyImpl;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.governator.auto.annotations.ConditionalOnProfile;

/**
 * @author Tomasz Bak
 */
@ConditionalOnProfile(ExtAbstractModule.READ_PROFILE)
public class Eureka1RestApiReadModule extends ExtAbstractModule {

    private static final String EUREKA1_CONFIG_PREFIX = "eureka2.ext.eureka1Rest";

    @Override
    protected void configure() {
        bind(Eureka1RegistryProxy.class).to(Eureka1RegistryProxyImpl.class);
        bind(Eureka1RootRequestHandler.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public Eureka1Configuration getEureka1Configuration(ConfigProxyFactory factory) {
        return factory.newProxy(Eureka1Configuration.class, EUREKA1_CONFIG_PREFIX);
    }
}
