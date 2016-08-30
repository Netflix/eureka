package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.Archaius2VipAddressResolver;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.appinfo.providers.VipAddressResolver;
import com.netflix.archaius.api.Config;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaArchaius2ClientConfig;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.EurekaArchaius2TransportConfig;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;

import javax.inject.Inject;

/**
 * @author David Liu
 */
final class InternalEurekaClientModule extends AbstractModule {

    private static class DiscoveryManagerInitializer {
        @Inject
        public static void initialize(DiscoveryManager discoveryManager) {

        }
    }


    @Override
    protected void configure() {
        // require binding for Config from archaius2
        requireBinding(Config.class);

        bind(ApplicationInfoManager.class).asEagerSingleton();

        // Bindings for eureka
        bind(EurekaTransportConfig.class).to(EurekaArchaius2TransportConfig.class);
        bind(EurekaClientConfig.class).to(EurekaArchaius2ClientConfig.class);

        bind(VipAddressResolver.class).to(Archaius2VipAddressResolver.class);
        bind(InstanceInfo.class).toProvider(EurekaConfigBasedInstanceInfoProvider.class);
        bind(EurekaClient.class).to(DiscoveryClient.class);

        // legacy DiscoveryManager set up
        requestStaticInjection(DiscoveryManagerInitializer.class);
    }

    @Override
    public boolean equals(Object obj) {
        return InternalEurekaClientModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return InternalEurekaClientModule.class.hashCode();
    }
}
