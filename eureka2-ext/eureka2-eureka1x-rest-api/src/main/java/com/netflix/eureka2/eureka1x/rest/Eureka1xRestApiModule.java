package com.netflix.eureka2.eureka1x.rest;

import com.netflix.eureka2.server.spi.ExtAbstractModule;

/**
 * @author Tomasz Bak
 */
public class Eureka1xRestApiModule extends ExtAbstractModule {

    private final Eureka1xConfiguration config;

    public Eureka1xRestApiModule() {
        this.config = new Eureka1xConfiguration();
    }

    public Eureka1xRestApiModule(Eureka1xConfiguration config) {
        this.config = config;
    }

    @Override
    public boolean isRunnable(ServerType serverType) {
        return serverType == ServerType.Write;
    }

    @Override
    protected void configure() {
        bind(Eureka1xQueryRequestHandler.class).asEagerSingleton();
        bind(Eureka1xConfiguration.class).toInstance(config);
    }
}
