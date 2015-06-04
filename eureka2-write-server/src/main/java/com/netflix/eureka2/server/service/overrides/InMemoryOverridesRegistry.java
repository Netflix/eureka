package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An in memory version of the overrides module primarily useful for testing purposes with the embedded cluster.
 *
 * @author David Liu
 */
@Singleton
public class InMemoryOverridesRegistry implements OverridesRegistry {

    private final int updatePeriod;
    private final TimeUnit updateTimeUnit;
    private final Scheduler scheduler;

    private final ConcurrentMap<String, Overrides> overridesMap;
    private final Observable<Map<String, Overrides>> overridesObservable;

    public InMemoryOverridesRegistry() {
        this(30, TimeUnit.SECONDS, Schedulers.computation());
    }

    public InMemoryOverridesRegistry(int updatePeriod, TimeUnit updateTimeUnit, Scheduler scheduler) {
        this.overridesMap = new ConcurrentHashMap<>();
        this.updatePeriod = updatePeriod;
        this.updateTimeUnit = updateTimeUnit;
        this.scheduler = scheduler;
        this.overridesObservable = getGlobalSnapshot();
    }

    @PreDestroy
    @Override
    public void shutdown() {
    }

    @Override
    public Overrides get(String id) {
        return overridesMap.get(id);
    }

    @Override
    public Observable<Void> set(Overrides overrides) {
        try {
            overridesMap.put(overrides.getId(), overrides);
            return Observable.empty();
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    @Override
    public Observable<Void> remove(String id) {
        try {
            overridesMap.remove(id);
            return Observable.empty();
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Observable<ChangeNotification<Overrides>> forUpdates(final String id) {
        final AtomicReference<Overrides> snapshot = new AtomicReference<>(null);
        final Overrides deleteRef = new Overrides(id, Collections.EMPTY_SET);

        return overridesObservable
                .map(new Func1<Map<String, Overrides>, ChangeNotification<Overrides>>() {
                    @Override
                    public ChangeNotification<Overrides> call(Map<String, Overrides> allOverrides) {
                        Overrides curr = allOverrides.get(id);
                        Overrides prev = snapshot.getAndSet(curr);

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
     * return the current snapshot of all global updates
     */
    private Observable<Map<String, Overrides>> getGlobalSnapshot() {

        return Observable.timer(0, updatePeriod, updateTimeUnit, scheduler)
                .map(new Func1<Long, Map<String, Overrides>>() {
                    @Override
                    public Map<String, Overrides> call(Long aLong) {
                        return new HashMap<>(overridesMap);
                    }
                })
                .replay(1)
                .refCount();
    }
}
