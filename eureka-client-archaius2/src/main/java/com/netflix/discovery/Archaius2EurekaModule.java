package com.netflix.discovery;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.archaius.Config;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;

/**
 * Add this module to your project to enable Eureka client and registration
 * 
 * @author elandau
 *
 */
public final class Archaius2EurekaModule extends AbstractModule {
    @Override
    protected void configure() {
        requireBinding(Config.class);
        
    	// Bindings for eureka
        bind(EurekaInstanceConfig.class).to(KaryonEurekaInstanceConfig.class);
        bind(EurekaClientConfig.class).to(KaryonEurekaClientConfig.class);
        bind(InstanceInfo.class).toProvider(EurekaConfigBasedInstanceInfoProvider.class);
        bind(EurekaClient.class).to(DiscoveryClient.class);
    }
    
    @Override
    public boolean equals(Object obj) {
        return Archaius2EurekaModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return Archaius2EurekaModule.class.hashCode();
    }
}
