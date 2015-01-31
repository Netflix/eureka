package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.client.registration.RegistrationResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action0;
import rx.observers.TestSubscriber;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author David Liu
 */
public class RegistrationResponseTest {

    private TestSubscriber<Void> testSubscriber1;
    private TestSubscriber<Void> testSubscriber2;

    @Before
    public void setUp() {
        testSubscriber1 = new TestSubscriber<>();
        testSubscriber2 = new TestSubscriber<>();
    }

    @Test
    public void testOnSubscribeTriggersSubscriptionToDelegate() {
        final AtomicInteger delegateSubscribeCount = new AtomicInteger(0);
        Observable<Void> delegate = Observable.<Void>empty().doOnSubscribe(new Action0() {
            @Override
            public void call() {
                delegateSubscribeCount.incrementAndGet();
            }
        });
        Observable<Void> init = Observable.empty();
        RegistrationResponse observable = RegistrationResponse.from(delegate, init);

        observable.subscribe(testSubscriber1);
        testSubscriber1.assertTerminalEvent();
        testSubscriber1.assertNoErrors();
        Assert.assertEquals(1, delegateSubscribeCount.get());

        observable.subscribe(testSubscriber2);
        testSubscriber2.assertTerminalEvent();
        testSubscriber2.assertNoErrors();
        Assert.assertEquals(2, delegateSubscribeCount.get());
    }

    @Test
    public void testDelegateOnErrorButInitOnComplete() {
        Exception e = new Exception("error");
        Observable<Void> delegate = Observable.error(e);
        Observable<Void> init = Observable.empty();
        RegistrationResponse observable = RegistrationResponse.from(delegate, init);

        observable.subscribe(testSubscriber1);
        testSubscriber1.assertTerminalEvent();
        Assert.assertEquals(1, testSubscriber1.getOnErrorEvents().size());
        Assert.assertEquals(e, testSubscriber1.getOnErrorEvents().get(0));

        observable.getInitObservable().subscribe(testSubscriber2);
        testSubscriber2.assertTerminalEvent();
        testSubscriber2.assertNoErrors();
    }

    @Test
    public void testDelegateOnCompleteButInitOnError() {
        Exception e = new Exception("error");
        Observable<Void> delegate = Observable.empty();
        Observable<Void> init = Observable.error(e);
        RegistrationResponse observable = RegistrationResponse.from(delegate, init);

        observable.subscribe(testSubscriber1);
        testSubscriber1.assertTerminalEvent();
        testSubscriber1.assertNoErrors();

        observable.getInitObservable().subscribe(testSubscriber2);
        testSubscriber2.assertTerminalEvent();
        Assert.assertEquals(1, testSubscriber2.getOnErrorEvents().size());
        Assert.assertEquals(e, testSubscriber2.getOnErrorEvents().get(0));
    }

}
