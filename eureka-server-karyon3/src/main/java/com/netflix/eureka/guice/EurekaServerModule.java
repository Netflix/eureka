package com.netflix.eureka.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.LookupService;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.DefaultEurekaServerContext;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.InstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl;
import com.netflix.eureka.resources.DefaultServerCodecs;
import com.netflix.eureka.resources.ServerCodecs;

/**
 * @author David Liu
 */
public class EurekaServerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EurekaServerConfig.class).to(DefaultEurekaServerConfig.class).in(Scopes.SINGLETON);
        bind(PeerEurekaNodes.class).in(Scopes.SINGLETON);
        bind(InstanceRegistry.class).to(PeerAwareInstanceRegistryImpl.class);
        bind(PeerAwareInstanceRegistry.class).to(PeerAwareInstanceRegistryImpl.class);
        bind(ServerCodecs.class).to(DefaultServerCodecs.class);

        bind(EurekaServerContext.class).to(DefaultEurekaServerContext.class);
    }
}
