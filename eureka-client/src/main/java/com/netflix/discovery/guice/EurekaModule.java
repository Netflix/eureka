package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.ApplicationInfoManager;

/**
 * @author David Liu
 */
public class EurekaModule extends AbstractModule {
    @Override
    protected void configure() {
        // need to eagerly initialize
        bind(ApplicationInfoManager.class).asEagerSingleton();

        // default bindings that can be overridden are:
        //  - EurekaInstanceConfig -> CloudInstanceConfig
        //  - Provider<InstanceInfo> -> EurekaConfigBasedInstanceInfoProvider
        //  - EurekaClientConfig -> DefaultEurekaClientConfig
        //  - EurekaClient -> DiscoveryClient
    }
}
