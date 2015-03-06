package com.netflix.eureka2.eureka1.rest.registry;

import com.netflix.eureka2.client.registration.RegistrationObservable;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;

/**
 * TODO Eureka client API mocking is very difficult now. We should provide easy to use helper class.
 *
 * @author Tomasz Bak
 */
public class TestableRegistrationObservable extends RegistrationObservable {

    public static Subscriber<? super Void> lifecycleSubscriber;

    public TestableRegistrationObservable(OnSubscribe<Void> onSubscribe, Observable<Void> initialRegistrationResult) {
        super(onSubscribe, initialRegistrationResult);
    }

    public static TestableRegistrationObservable create() {
        OnSubscribe<Void> lifecycle = new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                lifecycleSubscriber = subscriber;
            }
        };
        Observable<Void> initial = PublishSubject.create();
        return new TestableRegistrationObservable(lifecycle, initial);
    }
}
