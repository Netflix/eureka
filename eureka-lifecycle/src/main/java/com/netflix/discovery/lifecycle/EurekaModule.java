package com.netflix.discovery.lifecycle;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.LookupService;
import com.netflix.governator.guice.lazy.LazySingleton;

/**
 * Guice module that provides specific bindings for Eureka components.
 * 
 * Available bindings are,
 * 
 * @UpStatus    Supplier<Boolean> 
 * @UpStatus    Observable<Boolean> 
 * @DownStatus  Supplier<Boolean> 
 * DiscoveryClient
 * LookupService
 * EurekaClientConfig
 * InstanceInfo
 * ApplicationInfoManager
 * 
 * @author elandau
 *
 */
@Singleton
public class EurekaModule extends AbstractModule {

    /**
     * Lazy provider for DiscoveryManager with proper shutdown
     * @author elandau
     */
    @LazySingleton
    public static class DefaultDiscoveryManagerProvider implements Provider<DiscoveryManager> {
        private DiscoveryManager manager;
        
        @Override
        public DiscoveryManager get() {
            manager = DiscoveryManager.getInstance();
            return manager;
        }
        
        @PreDestroy
        public void shutdown() {
            if (manager != null) {
                manager.shutdownComponent();
            }
        }
    }
    
    /**
     * Base class for any lazy component that depends on DiscoveryManager
     * @author elandau
     *
     * @param <T>
     */
    public static abstract class LazyDiscoveryComponentProvider<T> implements Provider<T> {
        @Inject
        protected Provider<DiscoveryManager> manager;
    }
    
    @LazySingleton
    public static class DiscoveryClientProvider extends LazyDiscoveryComponentProvider<DiscoveryClient> {
        @Override
        public DiscoveryClient get() {
            return manager.get().getDiscoveryClient();
        }
    }
    
    @LazySingleton
    public static class LookupServiceProvider extends LazyDiscoveryComponentProvider<LookupService> {
        @Override
        public LookupService get() {
            return manager.get().getLookupService();
        }
    }
    
    @LazySingleton
    public static class EurekaClientConfigProvider extends LazyDiscoveryComponentProvider<EurekaClientConfig> {
        @Override
        public EurekaClientConfig get() {
            return manager.get().getEurekaClientConfig();
        }
    }
    
    @LazySingleton
    public static class ApplicationInfoManagerProvider extends LazyDiscoveryComponentProvider<ApplicationInfoManager> {
        @Override
        public ApplicationInfoManager get() {
            manager.get();
            return ApplicationInfoManager.getInstance();
        }
    }
    
    @LazySingleton
    public static class InstanceInfoProvider extends LazyDiscoveryComponentProvider<InstanceInfo> {
        @Override
        public InstanceInfo get() {
            manager.get();
            return ApplicationInfoManager.getInstance().getInfo();
        }
    }
    
    @Inject
    public EurekaModule(InternalEurekaStatusModule statusModule) {
        
    }
    
    @Override
    protected void configure() {
        // DiscoveryManager bindings
        
        bindDiscoveryManager();
        
        bind(DiscoveryClient.class)     
            .toProvider(DiscoveryClientProvider.class);
        
        bind(LookupService.class)
            .toProvider(LookupServiceProvider.class);
        
        bind(EurekaClientConfig.class)
            .toProvider(EurekaClientConfigProvider.class);

        // ApplicationInfo bindings
        
        bind(ApplicationInfoManager.class)
            .toProvider(ApplicationInfoManagerProvider.class);
        
        bind(InstanceInfo.class)
            .toProvider(InstanceInfoProvider.class);
    }
    
    /**
     * This binding has been pulled out to a separate method so it can be overwritten. 
     */
    protected void bindDiscoveryManager() {
        bind(DiscoveryManager.class)
            .toProvider(DefaultDiscoveryManagerProvider.class);
    }
}
