package com.netflix.discovery;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.google.common.base.Supplier;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.governator.annotations.binding.DownStatus;
import com.netflix.governator.annotations.binding.UpStatus;

/**
 * Specific bindings for eureka status checker.
 *
 * Note that this is an internal modules and ASSUMES that a binding for
 * DiscoveryClient was already set.
 *
 * Exposed bindings,
 *
 * &#64;UpStatus   Supplier<Boolean>
 * &#64;DownStatus Supplier<Boolean>
 * &#64;UpStatus   Observable<Boolean>
 *
 * @author elandau
 *
 */
@Singleton
public class InternalEurekaStatusModule extends AbstractModule {
    @Singleton
    public static class UpStatusProvider implements Provider<Supplier<Boolean>> {
        @Inject
        private Provider<EurekaUpStatusResolver> upStatus;

        @Override
        public Supplier<Boolean> get() {
            final EurekaUpStatusResolver resolver = upStatus.get();
            return new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return resolver.getStatus().equals(InstanceInfo.InstanceStatus.UP);
                }
            };
        }
    }

    @Singleton
    public static class DownStatusProvider implements Provider<Supplier<Boolean>> {
        @Inject
        private Provider<EurekaUpStatusResolver> upStatus;

        @Override
        public Supplier<Boolean> get() {
            final EurekaUpStatusResolver resolver = upStatus.get();
            return new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return !resolver.getStatus().equals(InstanceInfo.InstanceStatus.UP);
                }
            };
        }
    }

    @Override
    protected void configure() {
        bind(new TypeLiteral<Supplier<Boolean>>() {
        })
                .annotatedWith(UpStatus.class)
                .toProvider(UpStatusProvider.class);

        bind(new TypeLiteral<Supplier<Boolean>>() {
        })
                .annotatedWith(DownStatus.class)
                .toProvider(DownStatusProvider.class);
    }
}
