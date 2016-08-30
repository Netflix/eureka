package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.AbstractInstanceConfig;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.Archaius2VipAddressResolver;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.appinfo.providers.VipAddressResolver;
import com.netflix.archaius.api.Config;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaArchaius2ClientConfig;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.EurekaArchaius2TransportConfig;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;

/**
 * @author David Liu
 */
public final class LocalDevEurekaClientModule extends AbstractModule {
    @Override
    protected void configure() {
        // require binding for Config from archaius2
        requireBinding(Config.class);

        bind(ApplicationInfoManager.class).asEagerSingleton();

        // Bindings for eureka
        bind(EurekaInstanceConfig.class).to(EurekaArchaius2InstanceConfig.class);
        bind(AbstractInstanceConfig.class).to(EurekaArchaius2InstanceConfig.class);

        bind(EurekaTransportConfig.class).to(EurekaArchaius2TransportConfig.class);
        bind(EurekaClientConfig.class).to(EurekaArchaius2ClientConfig.class);

        bind(VipAddressResolver.class).to(Archaius2VipAddressResolver.class);
        bind(InstanceInfo.class).toProvider(EurekaConfigBasedInstanceInfoProvider.class);
        bind(EurekaClient.class).to(DiscoveryClient.class);
    }

    @Override
    public boolean equals(Object obj) {
        return LocalDevEurekaClientModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return LocalDevEurekaClientModule.class.hashCode();
    }
}
