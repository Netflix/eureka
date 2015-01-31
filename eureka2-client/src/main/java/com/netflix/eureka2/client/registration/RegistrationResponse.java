package com.netflix.eureka2.client.registration;

import rx.Observable;
import rx.Subscriber;

/**
 * An observable representing the lifecycle of a registration action. The lifecycle can also optionally contain
 * an init Observable<Void> that can onComplete or onError to report on the channel init status.
 *
 * @author David Liu
 */
public class RegistrationResponse extends Observable<Void> {
    private final Observable<Void> initObservable;

    protected RegistrationResponse(OnSubscribe<Void> onSubscribe, Observable<Void> initObservable) {
        super(onSubscribe);
        this.initObservable = initObservable;
    }

    public Observable<Void> getInitObservable() {
        return initObservable;
    }

    public static RegistrationResponse create(final OnSubscribe<Void> onSubscribe, Observable<Void> initObservable) {
        return new RegistrationResponse(onSubscribe, initObservable);
    }

    public static RegistrationResponse from(final Observable<Void> lifecycle, Observable<Void> initObservable) {
        return new RegistrationResponse(new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                lifecycle.subscribe(subscriber);
            }
        }, initObservable);
    }
}
