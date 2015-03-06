package com.netflix.eureka2.eureka1.rest;

import com.netflix.eureka2.server.spi.ExtAbstractModule;

/**
 * @author Tomasz Bak
 */
public class Eureka1RestApiModule extends ExtAbstractModule {

    private final Eureka1Configuration config;
    private ServerType serverType;

    public Eureka1RestApiModule() {
        this.config = new Eureka1Configuration();
    }

    public Eureka1RestApiModule(Eureka1Configuration config, ServerType serverType) {
        this.config = config;
        this.serverType = serverType;
    }

    @Override
    public boolean isRunnable(ServerType serverType) {
        return serverType == ServerType.Write || serverType == ServerType.Read;
    }

    @Override
    protected void configure() {
        bind(Eureka1Configuration.class).toInstance(config);
        if (serverType == ServerType.Write) {
            bind(Eureka1RedirectRequestHandler.class).asEagerSingleton();
        } else if (serverType == ServerType.Read) {
            bind(Eureka1RootRequestHandler.class).asEagerSingleton();
        }
    }
}
