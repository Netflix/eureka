package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A loading overrides registry that stores overrides in an external source, and periodically loads a global
 * snapshot to a locally backed hash map. Writes to this registry is passed on to the external overrides source,
 * and reads are returned from the local hash map cache.
 *
 * @author David Liu
 */
@Singleton
public class LoadingOverridesRegistry implements OverridesRegistry {
    private final int updatePeriod;
    private final TimeUnit updateTimeUnit;
    private final Scheduler scheduler;

    private final ExternalOverridesSource source;

    private final AtomicReference<Map<String, Overrides>> overridesSnapshot;

    private final BehaviorSubject<Map<String, Overrides>> overridesSubject;
    private final Subscription loadingSubscription;

    @Inject
    public LoadingOverridesRegistry(ExternalOverridesSource source) {
        this(source, 30, TimeUnit.SECONDS, Schedulers.computation());
    }

    public LoadingOverridesRegistry(ExternalOverridesSource source, int updatePeriod, TimeUnit updateTimeUnit, Scheduler scheduler) {
        this.source = source;
        this.updatePeriod = updatePeriod;
        this.updateTimeUnit = updateTimeUnit;
        this.scheduler = scheduler;

        this.overridesSnapshot = new AtomicReference<Map<String, Overrides>>(new HashMap<String, Overrides>());
        this.overridesSubject = BehaviorSubject.create();

        this.loadingSubscription = loadGlobalSnapshot().subscribe(overridesSubject);
    }

    @PreDestroy
    @Override
    public void shutdown() {
        loadingSubscription.unsubscribe();
    }

    @Override
    public Overrides get(String id) {
        return overridesSnapshot.get().get(id);
    }

    @Override
    public Observable<Void> set(Overrides overrides) {
        try {
            source.set(overrides);
            return Observable.empty();
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    @Override
    public Observable<Void> remove(String id) {
        try {
            source.remove(id);
            return Observable.empty();
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Observable<ChangeNotification<Overrides>> forUpdates(final String id) {
        final AtomicReference<Overrides> singleSnapshot = new AtomicReference<>(null);
        final Overrides deleteRef = new Overrides(id, Collections.EMPTY_SET);

        return overridesSubject
                .map(new Func1<Map<String, Overrides>, ChangeNotification<Overrides>>() {
                    @Override
                    public ChangeNotification<Overrides> call(Map<String, Overrides> allOverrides) {
                        Overrides curr = allOverrides.get(id);
                        Overrides prev = singleSnapshot.getAndSet(curr);

                        if (curr == null) {  // always return Delete regardless of prev is curr is null
                            return new ChangeNotification<>(ChangeNotification.Kind.Delete, deleteRef);
                        } else if (prev == null) {
                            return new ChangeNotification<>(ChangeNotification.Kind.Add, curr);
                        } else if (!prev.equals(curr)) {
                            return new ModifyNotification<>(curr, curr.getDeltas());
                        } else {
                            return null;
                        }
                    }
                })
                .filter(RxFunctions.filterNullValuesFunc())
                .distinctUntilChanged();
    }

    /**
     * return a periodic view of the current snapshot of all global updates as an observable
     */
    private Observable<Map<String, Overrides>> loadGlobalSnapshot() {
        return Observable.timer(0, updatePeriod, updateTimeUnit, scheduler)
                .map(new Func1<Long, Map<String, Overrides>>() {
                    @Override
                    public Map<String, Overrides> call(Long aLong) {
                        Map<String, Overrides> latest = source.asMap();
                        overridesSnapshot.set(latest);
                        return latest;
                    }
                });
    }


    /**
     * An interface over various external sources for storing overrides
     */
    public interface ExternalOverridesSource {

        void set(Overrides overrides) throws Exception;

        void remove(String id) throws Exception;

        Map<String, Overrides> asMap();
    }
}
