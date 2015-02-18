package com.netflix.eureka2.client.registration;

import rx.Observable;
import rx.Subscriber;

/**
 * An observable that represent a registration request. The registration begins when the request is subscribed to,
 * and ends when the request is unsubscribed from.
 *
 * The request observable also represent the success/error of the overall registration lifecycle and will onError
 * when it fails to maintain the registration to the server.
 *
 * An init observable is available from the registration request that returns status on the success/error of the
 * initial registration establishment with the remote server. This can be used for initial bootstrap needs if necessary.
 *
 * @author David Liu
 */
public class RegistrationRequest extends Observable<Void> {
    private final Observable<Void> initObservable;

    protected RegistrationRequest(OnSubscribe<Void> onSubscribe, Observable<Void> initObservable) {
        super(onSubscribe);
        this.initObservable = initObservable;
    }

    public Observable<Void> getInitObservable() {
        return initObservable;
    }

    public static RegistrationRequest create(final OnSubscribe<Void> onSubscribe, Observable<Void> initObservable) {
        return new RegistrationRequest(onSubscribe, initObservable);
    }

    public static RegistrationRequest from(final Observable<Void> lifecycle, Observable<Void> initObservable) {
        return new RegistrationRequest(new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                lifecycle.subscribe(subscriber);
            }
        }, initObservable);
    }
}
