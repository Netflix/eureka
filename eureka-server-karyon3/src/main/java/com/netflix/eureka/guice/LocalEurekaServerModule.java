package com.netflix.eureka.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.providers.DefaultEurekaClientConfigProvider;
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
public class LocalEurekaServerModule extends AbstractModule {
    @Override
    protected void configure() {
        // don't install the client module, bind the specific components for local use
        bind(ApplicationInfoManager.class).asEagerSingleton();

        bind(EurekaInstanceConfig.class).toProvider(MyDataCenterInstanceConfigProvider.class).in(Scopes.SINGLETON);
        bind(EurekaClientConfig.class).toProvider(DefaultEurekaClientConfigProvider.class).in(Scopes.SINGLETON);

        bind(InstanceInfo.class).toProvider(EurekaConfigBasedInstanceInfoProvider.class).in(Scopes.SINGLETON);
        bind(EurekaClient.class).to(DiscoveryClient.class).in(Scopes.SINGLETON);

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
}
