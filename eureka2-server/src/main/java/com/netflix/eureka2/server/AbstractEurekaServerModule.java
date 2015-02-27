package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.server.http.EurekaHttpServer;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEurekaServerModule extends AbstractModule {
    @Override
    protected void configure() {
        configureEureka();

        bind(EurekaHttpServer.class);
        bind(EurekaHealthStatusAggregator.class);
    }

    protected abstract void configureEureka();
}
