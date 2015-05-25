package com.netflix.eureka2.server.health;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.health.SubsystemDescriptor;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.server.utils.guice.PostInjector;
import rx.Observable;
import rx.subjects.ReplaySubject;

/**
 * Collects health status providers, and makes them available via {@link HealthStatusProviderRegistry#activate()}
 * method. If no provider is registered, a default {@link AlwaysHealthyStatusProvider} is returned, to
 * report always status UP.
 *
 * @author Tomasz Bak
 */
public class HealthStatusProviderRegistry {

    private final List<HealthStatusProvider<?>> providers = new CopyOnWriteArrayList<>();
    private final ReplaySubject<List<HealthStatusProvider<?>>> providerSubject = ReplaySubject.create();

    public void add(HealthStatusProvider<?> provider) {
        providers.add(provider);
    }

    public Observable<List<HealthStatusProvider<?>>> healthStatusProviders() {
        return providerSubject;
    }

    @PostInjector
    public void activate() {
        if (providers.isEmpty()) {
            List<HealthStatusProvider<?>> providers = Collections.<HealthStatusProvider<?>>singletonList(
                    new AlwaysHealthyStatusProvider()
            );
            providerSubject.onNext(providers);
        }
        providerSubject.onNext(providers);
    }

    static class AlwaysHealthyStatusProvider implements HealthStatusProvider<AlwaysHealthyStatusProvider> {

        private static final SubsystemDescriptor<AlwaysHealthyStatusProvider> DESCRIPTOR = new SubsystemDescriptor<>(
                AlwaysHealthyStatusProvider.class,
                "Always healthy status provider",
                "Healthcheck that always returns status UP"
        );

        @Override
        public Observable<HealthStatusUpdate<AlwaysHealthyStatusProvider>> healthStatus() {
            return Observable.just(new HealthStatusUpdate<AlwaysHealthyStatusProvider>(Status.UP, DESCRIPTOR));
        }
    }
}
