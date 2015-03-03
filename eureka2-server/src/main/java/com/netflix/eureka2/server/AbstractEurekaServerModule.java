package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.http.HealthConnectionHandler;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEurekaServerModule extends AbstractModule {
    @Override
    protected void configure() {
        configureEureka();

        bind(EurekaHttpServer.class).asEagerSingleton();
        bind(EurekaHealthStatusAggregator.class).asEagerSingleton();
        bind(HealthConnectionHandler.class).asEagerSingleton();
    }

    protected abstract void configureEureka();
}
