package com.netflix.eureka2.testkit.internal.rx;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import rx.Subscriber;
import rx.functions.Func1;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * RxJava {@link rx.observers.TestSubscriber}, is useful in most cases, specially when
 * pared with {@link rx.schedulers.TestScheduler}. Sometimes however we want to examine asynchronous
 * stream while it still produces items. This requires blocking not on the terminal event, but while
 * waiting for onNext to happen.
 * <p>
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

    private final Set<Thread> blockedThreads = new ConcurrentSkipListSet<>((t1, t2) -> Long.compare(t1.getId(), t2.getId()));

    @Override
    public void onCompleted() {
        if (!state.compareAndSet(State.Open, State.OnCompleted)) {
            contractErrors.add(new Exception("onComplete called on subscriber in state " + state));
        }
        awakeAllBlockedThreads();
    }

    @Override
    public void onError(Throwable e) {
        if (!state.compareAndSet(State.Open, State.OnError)) {
            contractErrors.add(new Exception("onError called on subscriber in state " + state));
        }
        onErrorResult.set(e);
        awakeAllBlockedThreads();
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

    public List<T> takeNext(int n) throws IllegalStateException {
        List<T> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            T next = takeNext();
            if (next == null) {
                if (result.size() != n) {
                    throw new IllegalStateException("Did not receive the required number of items: " + n + ", only received: " + result.size());
                }
                break;
            }
            result.add(next);
        }
        return result;
    }

    public List<T> takeNext(int n, long timeout, TimeUnit timeUnit) throws InterruptedException, IllegalStateException {
        List<T> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            T next = takeNext(timeout, timeUnit);
            if (next == null) {
                if (result.size() != n) {
                    throw new IllegalStateException("Did not receive the required number of items: " + n + ", only received: " + result.size());
                }
                break;
            }
            result.add(next);
        }
        return result;
    }

    public T takeNext(long timeout, TimeUnit timeUnit) throws InterruptedException {
        blockedThreads.add(Thread.currentThread());
        try {
            return available.poll(timeout, timeUnit);
        } catch (InterruptedException e) {
            if (onErrorResult.get() != null) {
                throw new RuntimeException(onErrorResult.get());
            }
            throw e;
        } finally {
            blockedThreads.remove(Thread.currentThread());
        }
    }

    public T takeNextOrWait() throws InterruptedException {
        blockedThreads.add(Thread.currentThread());
        try {
            return available.poll(24, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            if (onErrorResult.get() != null) {
                throw new RuntimeException(onErrorResult.get());
            }
            throw e;
        } finally {
            blockedThreads.remove(Thread.currentThread());
        }
    }

    public T getLatestItem() {
        if (items.isEmpty()) {
            return null;
        }
        return items.get(items.size() - 1);
    }

    public List<T> getOnNextItems() {
        return new ArrayList<>(items);
    }

    public List<T> takeNextOrWait(int n) throws InterruptedException {
        List<T> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            T next = takeNextOrWait();
            if (next == null) {
                break;
            }
            result.add(next);
        }
        return result;
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

    public void assertOpen() {
        assertThat(state.get(), is(equalTo(State.Open)));
    }

    public void assertOnCompleted() {
        assertThat(state.get(), is(equalTo(State.OnCompleted)));
    }

    public void assertOnCompleted(int timeout, TimeUnit timeUnit) throws Exception {
        long waitTimeInMs = timeUnit.toMillis(timeout);
        long minWait = Math.max(waitTimeInMs, 10);

        for (int i = 0; i < minWait; i += 10) {
            if (state.get() == State.OnCompleted) {
                assertTrue(true);
                return;
            }
            Thread.sleep(10);
        }

        assertTrue(false);
    }

    public void assertOnError() {
        assertThat(state.get(), is(equalTo(State.OnError)));
    }

    public void assertOnError(Throwable expected) {
        assertThat(state.get(), is(equalTo(State.OnError)));
        assertThat(onErrorResult.get(), is(equalTo(expected)));
    }

    public void assertOnError(Class<? extends Throwable> expected) {
        assertThat(state.get(), is(equalTo(State.OnError)));
        assertThat(onErrorResult.get().getClass(), is(equalTo(expected)));
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
                fail(formatAnyOrderFailure(next, expected.size(), left));
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
            if (next != null && !left.remove(next)) {
                fail(formatAnyOrderFailure(next, expected.size(), left));
            }
        }
    }

    private void awakeAllBlockedThreads() {
        blockedThreads.forEach(Thread::interrupt);
    }

    private static <R> String formatAnyOrderFailure(R found, int total, Set<R> left) {
        int consumed = total - left.size();
        StringBuilder sb = new StringBuilder();
        sb.append("Unexpected item found in the stream: ").append(found).append('\n');
        sb.append("    consumed already ").append(consumed).append('\n');
        sb.append("    left items on expected list:");
        for (R item : left) {
            sb.append('\n').append(item);
        }
        return sb.toString();
    }
}
