package com.netflix.eureka2.eureka1.rest;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxy;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxyImpl;
import com.netflix.eureka2.server.spi.ExtAbstractModule;

/**
 * @author Tomasz Bak
 */
public class Eureka1RestApiModule extends ExtAbstractModule {

    private final Eureka1Configuration config;
    private final EurekaRegistrationClient registrationClient;
    private ServerType serverType;

    public Eureka1RestApiModule() {
        this.config = new Eureka1Configuration();
        this.registrationClient = null;
        this.serverType = null;
    }

    public Eureka1RestApiModule(Eureka1Configuration config, ServerType serverType) {
        this.config = config;
        this.serverType = serverType;
        this.registrationClient = null;
    }

    public Eureka1RestApiModule(Eureka1Configuration config, EurekaRegistrationClient registrationClient) {
        this.config = config;
        this.registrationClient = registrationClient;
        this.serverType = ServerType.Read;
    }

    @Override
    public boolean isRunnable(ServerType serverType) {
        this.serverType = serverType;
        return serverType == ServerType.Write || serverType == ServerType.Read;
    }

    @Override
    protected void configure() {
        bind(Eureka1Configuration.class).toInstance(config);
        if (serverType == ServerType.Write) {
            bind(Eureka1RedirectRequestHandler.class).asEagerSingleton();
        } else if (serverType == ServerType.Read) {
            if (registrationClient == null) {
                bind(Eureka1RegistryProxy.class).to(Eureka1RegistryProxyImpl.class);
            } else {
                bind(Eureka1RegistryProxy.class).toInstance(new Eureka1RegistryProxyImpl(registrationClient));
            }
            bind(Eureka1RootRequestHandler.class).asEagerSingleton();
        }
    }
}
