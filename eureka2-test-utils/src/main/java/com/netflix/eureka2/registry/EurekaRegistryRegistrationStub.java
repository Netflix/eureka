package com.netflix.eureka2.registry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author David Liu
 */
public class EurekaRegistryRegistrationStub implements EurekaRegistry<InstanceInfo> {
    private static final Logger logger = LoggerFactory.getLogger(EurekaRegistryRegistrationStub.class);

    private final AtomicReference<Observable<InstanceInfo>> currSnapshot = new AtomicReference<>(Observable.<InstanceInfo>empty());

    private final Source localSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "test");
    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> registrationUpdateSubscriber = new ExtTestSubscriber<>();

    @Override
    public Observable<Void> connect(Source source, final Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
        return Observable.<Void>never()
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        registrationUpdates
                                .subscribe(registrationUpdateSubscriber);
                    }
                });
    }

    public ChangeNotification<InstanceInfo> getLatest(long timeout, TimeUnit timeUnit) throws Exception {
        return registrationUpdateSubscriber.takeNext(timeout, timeUnit);
    }

    public void verifyRegisteredWith(InstanceInfo expected) throws Exception {
        ChangeNotification<InstanceInfo> notification = getLatest(1, TimeUnit.SECONDS);
        assertThat(notification.isDataNotification(), is(true));
        InstanceInfo next = notification.getData();
        assertThat(next, is(notNullValue()));
        assertThat(next, is(equalTo(expected)));
    }

    public void verifyUnregistered(String expectedId) throws Exception {
        ChangeNotification<InstanceInfo> notification = getLatest(1, TimeUnit.SECONDS);
        assertThat(notification.isDataNotification(), is(true));
        InstanceInfo next = notification.getData();
        assertThat(next, is(notNullValue()));
        assertThat(next.getId(), is(expectedId));
    }

    public void setCurrSnapshot(InstanceInfo... instanceInfos) {
        currSnapshot.set(Observable.from(instanceInfos));
    }

    @Override
    public Observable<Long> evictAll(Source.SourceMatcher evictionMatcher) {
        throw new UnsupportedOperationException("not supported by this stub");
    }

    @Override
    public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
        throw new UnsupportedOperationException("not supported by this stub");
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("not supported by this stub");
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return Observable.error(cause);
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        return currSnapshot.get();
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
        return currSnapshot.get();
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        throw new UnsupportedOperationException("not supported by this stub");
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
        throw new UnsupportedOperationException("not supported by this stub");
    }

    @Override
    public Source getSource() {
        return localSource;
    }
}
