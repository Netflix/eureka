package com.netflix.eureka2.channel;

import rx.Observable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author David Liu
 */
public abstract class TestChannel<T extends ServiceChannel, E> implements ServiceChannel {
    public final Integer id;
    public final Queue<E> operations;

    public volatile boolean closeCalled;

    protected final T delegate;

    public TestChannel(T delegate, Integer id) {
        this.delegate = delegate;
        this.id = id;
        this.operations = new ConcurrentLinkedQueue<>();
        this.closeCalled = false;
    }

    public T getDelegate() {
        return delegate;
    }

    @Override
    public void close() {
        this.closeCalled = true;
        delegate.close();
    }

    @Override
    public void close(Throwable error) {
        this.closeCalled = true;
        delegate.close(error);
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return delegate.asLifecycleObservable();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ":" + id;
    }
}
