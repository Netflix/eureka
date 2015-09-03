package com.netflix.eureka2.registry;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import rx.Observable;
import rx.functions.Action0;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaRegistrationProcessorStub implements EurekaRegistrationProcessor<InstanceInfo> {
    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> registrationUpdateSubscriber = new ExtTestSubscriber<>();

    @Override
    public Observable<Void> connect(String id, Source source, final Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
        return Observable.<Void>never()
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        registrationUpdates.subscribe(registrationUpdateSubscriber);
                    }
                });
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
        ChangeNotification<InstanceInfo> notification = registrationUpdateSubscriber.getLatestItem();
        assertThat(notification.isDataNotification(), is(true));
        InstanceInfo next = notification.getData();
        assertThat(next, is(notNullValue()));
        assertThat(next, is(equalTo(expected)));
    }

    public void verifyRegistrationCompleted() {
        registrationUpdateSubscriber.assertOnCompleted();
    }

    @Override
    public Observable<Integer> sizeObservable() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }
}
