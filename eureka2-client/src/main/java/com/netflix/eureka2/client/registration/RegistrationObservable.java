package com.netflix.eureka2.client.registration;

import rx.Observable;
import rx.Subscriber;

/**
 * An observable that represent a registration request. The registration begins when the request is subscribed to,
 * and ends when the request is unsubscribed from.
 *
 * The request observable also represent the success/error of the overall registration lifecycle and will onError
 * when it fails to maintain the registration to the server. Rx retries can be applied to this observable to retry
 * on these cases.
 *
 * An init observable is available from the registration request that returns status on the success/error of the
 * initial registration establishment with the remote server. This can be used for initial bootstrap needs if necessary.
 *
 * @author David Liu
 */
public class RegistrationObservable extends Observable<Void> {
    private final Observable<Void> initialRegistrationResult;

    protected RegistrationObservable(OnSubscribe<Void> onSubscribe, Observable<Void> initialRegistrationResult) {
        super(onSubscribe);
        this.initialRegistrationResult = initialRegistrationResult;
    }

    /**
     * Note that subscribing to this observable does start the registration process. You must subscribe to the
     * RegistrationObservable to initiate registration.
     *
     * @return an observable that will onComplete or onError depending on whether the initial registration to the
     *         remote server is successful or not, if the registration has started.
     */
    public Observable<Void> initialRegistrationResult() {
        return initialRegistrationResult;
    }

    static RegistrationObservable create(final OnSubscribe<Void> onSubscribe, Observable<Void> initObservable) {
        return new RegistrationObservable(onSubscribe, initObservable);
    }

    static RegistrationObservable from(final Observable<Void> lifecycle, Observable<Void> initObservable) {
        return new RegistrationObservable(new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                lifecycle.subscribe(subscriber);
            }
        }, initObservable);
    }
}
