package com.netflix.discovery;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.archaius.Config;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaArchaius2ClientConfig;

/**
 * Add this module to your project to enable Eureka client and registration
 * 
 * @author elandau
 *
 */
public final class EurekaArchaius2Module extends AbstractModule {
    @Override
    protected void configure() {
        requireBinding(Config.class);
        
    	// Bindings for eureka
        bind(EurekaInstanceConfig.class).to(EurekaInstanceConfig.class);
        bind(EurekaArchaius2ClientConfig.class).to(EurekaArchaius2ClientConfig.class);
        bind(InstanceInfo.class).toProvider(EurekaConfigBasedInstanceInfoProvider.class);
        bind(EurekaClient.class).to(DiscoveryClient.class);
    }
    
    @Override
    public boolean equals(Object obj) {
        return EurekaArchaius2Module.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return EurekaArchaius2Module.class.hashCode();
    }
}
