package com.netflix.eureka2.utils.rx;

import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
public class SettableSubscriberTest {

    private final TestSettableSubscriber<Integer> settableSubscriber = new TestSettableSubscriber<>();
    private final TestSubscriber<Integer> internalSubscriber = new TestSubscriber<>();
    private final List<Integer> items = Arrays.asList(1,2,3,4,5);

    @Test
    public void testPropagateOnNext() throws Exception {
        settableSubscriber.setWrapped(internalSubscriber);

        Observable.from(items)
                .subscribe(settableSubscriber);

        assertThat(settableSubscriber.awaitOnCompleted(1, TimeUnit.SECONDS), is(true));
        internalSubscriber.assertReceivedOnNext(items);
    }

    @Test
    public void testMaskOnCompleted() throws Exception {
        settableSubscriber.setWrapped(internalSubscriber);

        Observable.from(items)
                .subscribe(settableSubscriber);

        assertThat(settableSubscriber.awaitOnCompleted(1, TimeUnit.SECONDS), is(true));
        internalSubscriber.assertReceivedOnNext(items);
        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));

        settableSubscriber.setOnComplete();
        assertThat(internalSubscriber.getOnCompletedEvents().size(), is(1));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));
    }

    @Test
    public void testMaskOnError() throws Exception {
        settableSubscriber.setWrapped(internalSubscriber);
        Exception e = new IllegalStateException("foo");

        Observable.from(items).concatWith(Observable.error(e).cast(Integer.class))
                .subscribe(settableSubscriber);

        assertThat(settableSubscriber.awaitOnError(1, TimeUnit.SECONDS), is(true));
        internalSubscriber.assertReceivedOnNext(items);
        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));

        settableSubscriber.setOnError(e);
        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents().size(), is(1));
    }

    @Test
    public void testRxContract1() throws Exception {
        settableSubscriber.setWrapped(internalSubscriber);
        Exception e = new IllegalStateException("foo");

        Observable.<Integer>empty()
                .subscribe(settableSubscriber);

        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));

        settableSubscriber.setOnComplete();
        assertThat(internalSubscriber.getOnCompletedEvents().size(), is(1));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));

        settableSubscriber.setOnComplete();
        assertThat(internalSubscriber.getOnCompletedEvents().size(), is(1));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));

        settableSubscriber.setOnError(e);
        assertThat(internalSubscriber.getOnCompletedEvents().size(), is(1));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));
    }

    @Test
    public void testRxContract2() throws Exception {
        settableSubscriber.setWrapped(internalSubscriber);
        Exception e = new IllegalStateException("foo");

        Observable.<Integer>empty()
                .subscribe(settableSubscriber);

        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents(), is(empty()));

        settableSubscriber.setOnError(e);
        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents().size(), is(1));

        settableSubscriber.setOnError(e);
        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents().size(), is(1));

        settableSubscriber.setOnComplete();
        assertThat(internalSubscriber.getOnCompletedEvents(), is(empty()));
        assertThat(internalSubscriber.getOnErrorEvents().size(), is(1));
    }

    static class TestSettableSubscriber<T> extends SettableSubscriber<T> {
        private CountDownLatch onCompletedLatch = new CountDownLatch(1);
        private CountDownLatch onErrorLatch = new CountDownLatch(1);

        @Override
        public void onCompleted() {
            super.onCompleted();
            onCompletedLatch.countDown();
        }

        @Override
        public void onError(Throwable e) {
            super.onError(e);
            onErrorLatch.countDown();
        }

        public boolean awaitOnCompleted(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return onCompletedLatch.await(timeout, timeUnit);
        }

        public boolean awaitOnError(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return onErrorLatch.await(timeout, timeUnit);
        }
    }
}
