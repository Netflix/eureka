package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An overrides source implementation that stores overrides in memory. This is designed for test and embedded use
 * and not recommend for production usage. Either supply an implementation with a real datastore backing,
 * or use one of the available AWS implementations in eureka2-ext:eureka2-aws.
 *
 * @author David Liu
 */
@Singleton
public class InMemoryStatusOverridesRegistry implements InstanceStatusOverridesView, InstanceStatusOverridesSource {

    private final ConcurrentMap<String, Boolean> overridesMap;
    private final BehaviorSubject<Map<String, Boolean>> overridesSubject;

    public InMemoryStatusOverridesRegistry() {
        this.overridesMap = new ConcurrentHashMap<>();
        this.overridesSubject = BehaviorSubject.create();
        overridesSubject.onNext(overridesMap);
    }

    @PreDestroy
    public void shutdown() {
        overridesSubject.onCompleted();
        overridesMap.clear();
    }

    @Override
    public Observable<Boolean> setOutOfService(final InstanceInfo instanceInfo) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                try {
                    overridesMap.put(instanceInfo.getId(), true);
                    triggerUpdate();

                    subscriber.onNext(true);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        }).cache(1);
    }

    @Override
    public Observable<Boolean> unsetOutOfService(final InstanceInfo instanceInfo) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                overridesMap.remove(instanceInfo.getId());
                triggerUpdate();

                subscriber.onNext(true);
                subscriber.onCompleted();
            }
        }).cache(1);
    }

    @Override
    public Observable<Boolean> shouldApplyOutOfService(final InstanceInfo instanceInfo) {
        return overridesSubject
                .map(new Func1<Map<String, Boolean>, Boolean>() {
                    @Override
                    public Boolean call(Map<String, Boolean> map) {
                        return map.containsKey(instanceInfo.getId());
                    }
                })
                .distinctUntilChanged()
                .cache(1);
    }

    private void triggerUpdate() {
        overridesSubject.onNext(overridesMap);
    }
}
