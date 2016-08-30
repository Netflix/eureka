package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.AbstractInstanceConfig;
import com.netflix.appinfo.AmazonInfoConfig;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.Archaius2AmazonInfoConfig;
import com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig;
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
 * Add this module to your project to enable Eureka client and registration
 *
 * @author elandau
 *
 */
public final class Ec2EurekaClientModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new InternalEurekaClientModule());

        bind(AmazonInfoConfig.class).to(Archaius2AmazonInfoConfig.class);
        bind(EurekaInstanceConfig.class).to(Ec2EurekaArchaius2InstanceConfig.class);
        bind(AbstractInstanceConfig.class).to(Ec2EurekaArchaius2InstanceConfig.class);
        bind(EurekaArchaius2InstanceConfig.class).to(Ec2EurekaArchaius2InstanceConfig.class);
    }

    @Override
    public boolean equals(Object obj) {
        return Ec2EurekaClientModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return Ec2EurekaClientModule.class.hashCode();
    }
}
