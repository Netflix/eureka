package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.CloudInstanceConfigProvider;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.discovery.AbstractDiscoveryClientOptionalArgs;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.Jersey2DiscoveryClientOptionalArgs;
import com.netflix.discovery.providers.DefaultEurekaClientConfigProvider;
import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;
import com.netflix.discovery.shared.transport.jersey2.Jersey2TransportClientFactories;

/**
 * @author David Liu
 */
public final class Jersey2EurekaModule extends AbstractModule {
    @Override
    protected void configure() {
        // need to eagerly initialize
        bind(ApplicationInfoManager.class).asEagerSingleton();

        //
        // override these in additional modules if necessary with Modules.override()
        //

        bind(EurekaInstanceConfig.class).toProvider(CloudInstanceConfigProvider.class).in(Scopes.SINGLETON);
        bind(EurekaClientConfig.class).toProvider(DefaultEurekaClientConfigProvider.class).in(Scopes.SINGLETON);

        // this is the self instanceInfo used for registration purposes
        bind(InstanceInfo.class).toProvider(EurekaConfigBasedInstanceInfoProvider.class).in(Scopes.SINGLETON);

        bind(EurekaClient.class).to(DiscoveryClient.class).in(Scopes.SINGLETON);

        // jersey2 support bindings
        bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey2DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
    }

    @Provides
    public TransportClientFactories getTransportClientFactories() {
        return Jersey2TransportClientFactories.getInstance();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && getClass().equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
