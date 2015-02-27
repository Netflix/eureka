package com.netflix.eureka2.server.health;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.server.utils.guice.PostInjector;
import rx.Observable;
import rx.subjects.ReplaySubject;

/**
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
        providerSubject.onNext(providers);
    }
}
