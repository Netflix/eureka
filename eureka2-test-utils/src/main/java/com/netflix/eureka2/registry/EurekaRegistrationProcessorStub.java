package com.netflix.eureka2.registry;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaRegistrationProcessorStub implements EurekaRegistrationProcessor<InstanceInfo> {

    private final ExtTestSubscriber<InstanceInfo> registrationUpdateSubscriber = new ExtTestSubscriber<>();

    @Override
    public Observable<Void> register(String id, Source source, final Observable<InstanceInfo> registrationUpdates) {
        return Observable.create(new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                registrationUpdates.subscribe(registrationUpdateSubscriber);
                subscriber.onCompleted();

            }
        });
    }

    @Override
    public Observable<Boolean> register(InstanceInfo instanceInfo, Source source) {
        return null;
    }

    @Override
    public Observable<Boolean> unregister(InstanceInfo instanceInfo, Source source) {
        return null;
    }

    @Override
    public Observable<Void> shutdown() {
        return null;
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return null;
    }

    public void verifyRegisteredWith(InstanceInfo expected) {
        InstanceInfo next = registrationUpdateSubscriber.takeNext();
        assertThat(next, is(notNullValue()));
        assertThat(next, is(equalTo(expected)));
    }

    public void verifyRegistrationActive() {
        registrationUpdateSubscriber.assertOpen();
    }

    public void verifyRegistrationCompleted() {
        registrationUpdateSubscriber.assertOnCompleted();
    }
}
