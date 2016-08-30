package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.AbstractInstanceConfig;
import com.netflix.appinfo.EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaInstanceConfig;

/**
 * @author David Liu
 */
public final class LocalDevEurekaClientModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new InternalEurekaClientModule());

        bind(EurekaInstanceConfig.class).to(EurekaArchaius2InstanceConfig.class);
        bind(AbstractInstanceConfig.class).to(EurekaArchaius2InstanceConfig.class);
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
