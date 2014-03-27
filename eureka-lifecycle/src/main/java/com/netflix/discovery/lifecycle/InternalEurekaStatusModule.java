package com.netflix.discovery.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

import com.google.common.base.Supplier;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.netflix.governator.annotations.binding.DownStatus;
import com.netflix.governator.annotations.binding.UpStatus;
import com.netflix.governator.guice.lazy.LazySingleton;

/**
 * Specific bindings for eureka status checker.  
 * 
 * Note that this is an internal modules and ASSUMES that a binding for 
 * DiscoveryClient was already set.  
 * 
 * Exposed bindings,
 * 
 * @UpStatus   Supplier<Boolean>
 * @DownStatus Supplier<Boolean>
 * @UpStatus   Observable<Boolean>
 * 
 * @author elandau
 *
 */
@Singleton
class InternalEurekaStatusModule extends AbstractModule {
    
    @LazySingleton
    public static class UpStatusProvider implements Provider<Supplier<Boolean>> {
        @Inject
        private Provider<EurekaUpStatusResolver> upStatus;
        
        @Override
        public Supplier<Boolean> get() {
            final AtomicBoolean isUp = upStatus.get().getUpStatus();
            return new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return isUp.get();
                }
            };
        }
    }
    
    @LazySingleton
    public static class DownStatusProvider implements Provider<Supplier<Boolean>> {
        @Inject
        private Provider<EurekaUpStatusResolver> upStatus;
        
        @Override
        public Supplier<Boolean> get() {
            final AtomicBoolean isUp = upStatus.get().getUpStatus();
            return new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return !isUp.get();
                }
            };
        }
    }

    @LazySingleton
    public static class ObservableStatusProvider implements Provider<Observable<Boolean>> {
        @Inject
        private Provider<EurekaUpStatusResolver> upStatus;
        
        @Override
        public Observable<Boolean> get() {
            return upStatus.get().asObservable();
        }
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<Supplier<Boolean>>() {})
            .annotatedWith(UpStatus.class)
            .toProvider(UpStatusProvider.class);
        
        bind(new TypeLiteral<Supplier<Boolean>>() {})
            .annotatedWith(DownStatus.class)
            .toProvider(DownStatusProvider.class);
        
        bind(new TypeLiteral<Observable<Boolean>>() {})
            .annotatedWith(UpStatus.class)
            .toProvider(ObservableStatusProvider.class);
        
    }
}
