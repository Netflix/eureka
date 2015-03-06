package com.netflix.eureka2.eureka1x.rest;

import com.netflix.eureka2.server.spi.ExtAbstractModule;

/**
 * @author Tomasz Bak
 */
public class Eureka1xRestApiModule extends ExtAbstractModule {

    private final Eureka1xConfiguration config;
    private ServerType serverType;

    public Eureka1xRestApiModule() {
        this.config = new Eureka1xConfiguration();
    }

    public Eureka1xRestApiModule(Eureka1xConfiguration config, ServerType serverType) {
        this.config = config;
        this.serverType = serverType;
    }

    @Override
    public boolean isRunnable(ServerType serverType) {
        return serverType == ServerType.Write || serverType == ServerType.Read;
    }

    @Override
    protected void configure() {
        bind(Eureka1xConfiguration.class).toInstance(config);
        if (serverType == ServerType.Write) {
            bind(Eureka1xRedirectRequestHandler.class).asEagerSingleton();
        } else if (serverType == ServerType.Read) {
            bind(Eureka1xRootRequestHandler.class).asEagerSingleton();
        }
    }
}
