package com.netflix.discovery;

import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.base.Supplier;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.governator.annotations.binding.DownStatus;
import com.netflix.governator.annotations.binding.UpStatus;

/**
 * Guice module that provides specific bindings to Eureka components.
 * 
 * Available bindings are,
 * 
 * @UpStatus AtomicBoolean upStatus
 * @UpStatus Supplier<Boolean> upStatus
 * @DownStatus Supplier<Boolean> upStatus
 * DiscoveryClient
 * InstanceInfo
 * 
 * @author elandau
 *
 */
public class EurekaModule extends AbstractModule {

    private AtomicBoolean upStatus = new AtomicBoolean();
    
    @Override
    protected void configure() {
        bind(AtomicBoolean.class)
            .annotatedWith(UpStatus.class)
            .toInstance(upStatus);
        
        bind(new TypeLiteral<Supplier<Boolean>>() {})
            .annotatedWith(UpStatus.class)
            .toInstance(new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return upStatus.get();
                }
            });
        
        bind(new TypeLiteral<Supplier<Boolean>>() {})
            .annotatedWith(DownStatus.class)
            .toInstance(new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return !upStatus.get();
                }
            });
        
        bind(DiscoveryStatusChecker.class);
    }
    
    @Provides
    @Singleton
    public DiscoveryClient getDiscoveryClient() {
        return DiscoveryManager.getInstance().getDiscoveryClient();
    }

    @Provides
    @Singleton
    public InstanceInfo getInstanceInfo() {
        return ApplicationInfoManager.getInstance().getInfo();
    }

}
