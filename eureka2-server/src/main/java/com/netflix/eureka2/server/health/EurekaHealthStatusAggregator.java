package com.netflix.eureka2.server.health;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.health.HealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.health.SubsystemDescriptor;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func1;
import rx.functions.FuncN;
import rx.subjects.BehaviorSubject;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaHealthStatusAggregator implements HealthStatusAggregator<EurekaHealthStatusAggregator> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaHealthStatusAggregator.class);

    private static final Map<Status, Integer> STATUS_PRIORITY_MAP;

    static {
        STATUS_PRIORITY_MAP = new EnumMap<>(Status.class);
        STATUS_PRIORITY_MAP.put(Status.UNKNOWN, 0);
        STATUS_PRIORITY_MAP.put(Status.OUT_OF_SERVICE, 1);
        STATUS_PRIORITY_MAP.put(Status.DOWN, 2);
        STATUS_PRIORITY_MAP.put(Status.STARTING, 3);
        STATUS_PRIORITY_MAP.put(Status.UP, 4);
    }

    private static final SubsystemDescriptor<EurekaHealthStatusAggregator> DESCRIPTOR = new SubsystemDescriptor<>(
            EurekaHealthStatusAggregator.class,
            "Aggregated health check status",
            "Aggregated health check status"
    );

    private final HealthStatusProviderRegistry registry;

    private AtomicReference<Observable<HealthStatusUpdate<EurekaHealthStatusAggregator>>> aggregatedHealthObservable = new AtomicReference<>();
    private Subscription subscription;

    @Inject
    public EurekaHealthStatusAggregator(HealthStatusProviderRegistry registry) {
        this.registry = registry;
    }

    protected Observable<HealthStatusUpdate<EurekaHealthStatusAggregator>> connect() {
        Observable<HealthStatusUpdate<EurekaHealthStatusAggregator>> updateObservable = registry.healthStatusProviders()
                .flatMap(new Func1<List<HealthStatusProvider<?>>, Observable<Status>>() {
                    @Override
                    public Observable<Status> call(List<HealthStatusProvider<?>> healthStatusProviders) {
                        List<Observable<HealthStatusUpdate<?>>> healthObservables = new ArrayList<>(healthStatusProviders.size());
                        for (final HealthStatusProvider provider : healthStatusProviders) {
                            Observable<HealthStatusUpdate<?>> updateObservable = provider.healthStatus();
                            healthObservables.add(updateObservable);
                        }

                        return Observable.combineLatest(healthObservables, new FuncN<Status>() {
                            @Override
                            public Status call(Object... updates) {
                                Status aggregate = Status.UP;
                                for (Object update : updates) {
                                    Status next = ((HealthStatusUpdate<?>) update).getStatus();
                                    if (STATUS_PRIORITY_MAP.get(next) < STATUS_PRIORITY_MAP.get(aggregate)) {
                                        aggregate = next;
                                    }
                                }
                                return aggregate;
                            }
                        });
                    }
                })
                .distinctUntilChanged()
                .map(new Func1<Status, HealthStatusUpdate<EurekaHealthStatusAggregator>>() {
                    @Override
                    public HealthStatusUpdate<EurekaHealthStatusAggregator> call(Status status) {
                        logger.info("New health status update: {}", status);
                        return new HealthStatusUpdate<EurekaHealthStatusAggregator>(status, DESCRIPTOR);
                    }
                });
        BehaviorSubject<HealthStatusUpdate<EurekaHealthStatusAggregator>> subject = BehaviorSubject.create();
        updateObservable.subscribe(subject);
        return subject;
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    @Override
    public Observable<List<HealthStatusProvider<?>>> components() {
        return registry.healthStatusProviders();
    }

    @Override
    public Observable<HealthStatusUpdate<EurekaHealthStatusAggregator>> healthStatus() {
        if (aggregatedHealthObservable.get() != null) {
            return aggregatedHealthObservable.get();
        }
        aggregatedHealthObservable.compareAndSet(null, connect());
        return aggregatedHealthObservable.get();
    }
}
