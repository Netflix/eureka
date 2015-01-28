package com.netflix.eureka2.utils.rx;

import org.junit.Assert;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action0;
import rx.observers.TestSubscriber;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author David Liu
 */
public class BreakerSwitchOperatorTest {

    private final BreakerSwitchOperator<Object> operator = new BreakerSwitchOperator<>();

    private final TestSubscriber<Object> testSubscriber1 = new TestSubscriber<>();
    private final TestSubscriber<Object> testSubscriber2 = new TestSubscriber<>();

    @Test(timeout = 60000)
    public void testCloseOnCompleteAllSubscribers() throws Exception {
        final AtomicInteger unsubscribeCount = new AtomicInteger();

        Observable<Object> stream = Observable.never().doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                unsubscribeCount.incrementAndGet();
            }
        }).lift(operator);

        stream.subscribe(testSubscriber1);
        stream.subscribe(testSubscriber2);

        operator.close();

        testSubscriber1.awaitTerminalEvent(5, TimeUnit.SECONDS);
        testSubscriber2.awaitTerminalEvent(5, TimeUnit.SECONDS);

        testSubscriber1.assertTerminalEvent();
        testSubscriber1.assertNoErrors();

        testSubscriber2.assertTerminalEvent();
        testSubscriber2.assertNoErrors();

        Assert.assertEquals(2, unsubscribeCount.get());
    }
}
