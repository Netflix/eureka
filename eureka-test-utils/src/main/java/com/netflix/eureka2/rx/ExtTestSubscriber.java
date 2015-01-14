package com.netflix.eureka2.rx;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rx.Subscriber;
import rx.functions.Func1;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * RxJava {@link rx.observers.TestSubscriber}, is useful in most cases, specially when
 * pared with {@link rx.schedulers.TestScheduler}. Sometimes however we want to examine asynchronous
 * stream while it still produces items. This requires blocking not on the terminal event, but while
 * waiting for onNext to happen.
 *
 * Another difference of this class is a richer set of assertions.
 *
 * @author Tomasz Bak
 */
public class ExtTestSubscriber<T> extends Subscriber<T> {

    private enum State {Open, OnCompleted, OnError}

    private final AtomicReference<State> state = new AtomicReference<>(State.Open);

    private final List<T> items = new CopyOnWriteArrayList<>();
    private final BlockingQueue<T> available = new LinkedBlockingQueue<>();
    private final AtomicReference<Throwable> onErrorResult = new AtomicReference<>();

    private final List<Exception> contractErrors = new CopyOnWriteArrayList<>();

    @Override
    public void onCompleted() {
        if (!state.compareAndSet(State.Open, State.OnCompleted)) {
            contractErrors.add(new Exception("onComplete called on subscriber in state " + state));
        }
    }

    @Override
    public void onError(Throwable e) {
        if (!state.compareAndSet(State.Open, State.OnError)) {
            contractErrors.add(new Exception("onError called on subscriber in state " + state));
        }
        onErrorResult.set(e);
    }

    @Override
    public void onNext(T t) {
        if (state.get() != State.Open) {
            contractErrors.add(new Exception("onNext called on subscriber in state " + state));
        }
        items.add(t);
        available.add(t);
    }

    public T takeNext() {
        return available.poll();
    }

    public T takeNext(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return available.poll(timeout, timeUnit);
    }

    public T takeNextOrWait() throws InterruptedException {
        return available.poll(24, TimeUnit.HOURS);
    }

    public T takeNextOrFail() {
        T next = available.poll();
        if (next == null) {
            if (state.get() == State.Open) {
                fail("No more items currently available; observable stream is still open");
            } else {
                fail("No more items available; stream is terminated with state " + state);
            }
        }
        return next;
    }

    public void assertOnCompleted() {
        assertThat(state.get(), is(equalTo(State.OnCompleted)));
    }

    public void assertOnError() {
        assertThat(state.get(), is(equalTo(State.OnError)));
    }

    public void assertContainsInAnyOrder(Collection<T> expected) {
        assertContainsInAnyOrder(expected, new Func1<T, T>() {
            @Override
            public T call(T item) {
                return item;
            }
        });
    }

    public <R> void assertContainsInAnyOrder(Collection<R> expected, Func1<T, R> mapFun) {
        HashSet<R> left = new HashSet<>(expected);
        while (!left.isEmpty()) {
            R next = mapFun.call(takeNextOrFail());
            if (!left.remove(next)) {
                fail("Unexpected item found in the stream " + next);
            }
        }
    }

    public void assertProducesInAnyOrder(Collection<T> expected) throws InterruptedException {
        assertProducesInAnyOrder(expected, new Func1<T, T>() {
            @Override
            public T call(T item) {
                return item;
            }
        }, 24, TimeUnit.HOURS);
    }

    public <R> void assertProducesInAnyOrder(Collection<R> expected,
                                             Func1<T, R> mapFun) throws InterruptedException {
        assertProducesInAnyOrder(expected, mapFun, 24, TimeUnit.HOURS);
    }

    public <R> void assertProducesInAnyOrder(Collection<R> expected,
                                             Func1<T, R> mapFun,
                                             long timeout,
                                             TimeUnit timeUnit) throws InterruptedException {
        HashSet<R> left = new HashSet<>(expected);
        while (!left.isEmpty()) {
            R next = mapFun.call(takeNext(timeout, timeUnit));
            if (!left.remove(next)) {
                fail("Unexpected item found in the stream " + next);
            }
        }
    }
}
