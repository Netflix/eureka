package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.subjects.BehaviorSubject;

/**
 * A delegate self info resolver that caches the most recent info for multiple subscribers.
 *
 * @author David Liu
 */
public class CachingSelfInfoResolver implements SelfInfoResolver {

    private final Observable<InstanceInfo> delegateObservable;
    private final Observable<InstanceInfo> control;
    private final BehaviorSubject<InstanceInfo> cachingSubject;

    private volatile Subscription subscription;

    public CachingSelfInfoResolver(SelfInfoResolver delegate) {
        this.delegateObservable = delegate.resolve();
        this.cachingSubject = BehaviorSubject.create();

        control = Observable.<InstanceInfo>never()
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        subscription = delegateObservable.subscribe(cachingSubject);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if (subscription != null) {
                            subscription.unsubscribe();
                        }
                    }
                })
                .share();
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return cachingSubject.asObservable().mergeWith(control);
    }
}
