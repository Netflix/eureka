package com.netflix.eureka.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.DefaultEurekaServerContext;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.AbstractInstanceRegistry;
import com.netflix.eureka.registry.InstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl;
import com.netflix.eureka.resources.DefaultServerCodecs;
import com.netflix.eureka.resources.ServerCodecs;

/**
 * @author David Liu
 */
public class LocalDevEurekaServerModule extends AbstractModule {
    @Override
    protected void configure() {
        // server bindings
        bind(EurekaServerConfig.class).to(DefaultEurekaServerConfig.class).in(Scopes.SINGLETON);
        bind(PeerEurekaNodes.class).in(Scopes.SINGLETON);

        // registry and interfaces
        bind(PeerAwareInstanceRegistryImpl.class).asEagerSingleton();
        bind(InstanceRegistry.class).to(PeerAwareInstanceRegistryImpl.class);
        bind(AbstractInstanceRegistry.class).to(PeerAwareInstanceRegistryImpl.class);
        bind(PeerAwareInstanceRegistry.class).to(PeerAwareInstanceRegistryImpl.class);

        bind(ServerCodecs.class).to(DefaultServerCodecs.class).in(Scopes.SINGLETON);

        bind(EurekaServerContext.class).to(DefaultEurekaServerContext.class).in(Scopes.SINGLETON);
    }

    @Override
    public boolean equals(Object obj) {
        return LocalDevEurekaServerModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return LocalDevEurekaServerModule.class.hashCode();
    }
}
