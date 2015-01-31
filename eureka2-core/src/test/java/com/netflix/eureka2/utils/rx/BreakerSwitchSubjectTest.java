package com.netflix.eureka2.utils.rx;

import org.junit.Assert;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action0;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author David Liu
 */
public class BreakerSwitchSubjectTest {

    private final BreakerSwitchSubject<Object> subject = BreakerSwitchSubject.create(ReplaySubject.create());

    private final TestSubscriber<Object> testSubscriber1 = new TestSubscriber<>();
    private final TestSubscriber<Object> testSubscriber2 = new TestSubscriber<>();

    @Test
    public void testCloseOnCompleteAllSubscribers() throws Exception {
        final AtomicInteger unsubscribeCount = new AtomicInteger();

        Observable<Object> stream = subject.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                unsubscribeCount.incrementAndGet();
            }
        });

        stream.subscribe(testSubscriber1);
        stream.subscribe(testSubscriber2);

        subject.close();

        testSubscriber1.awaitTerminalEvent(5, TimeUnit.SECONDS);
        testSubscriber2.awaitTerminalEvent(5, TimeUnit.SECONDS);

        testSubscriber1.assertTerminalEvent();
        testSubscriber1.assertNoErrors();

        testSubscriber2.assertTerminalEvent();
        testSubscriber2.assertNoErrors();

        Assert.assertEquals(2, unsubscribeCount.get());
    }
}
