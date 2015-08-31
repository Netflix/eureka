package com.netflix.eureka2.utils.rx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;
import rx.observers.SerializedSubscriber;

import java.util.concurrent.atomic.AtomicReference;

/**
 * TODO: remove onCompleted and onError logging once behaviour is well verified
 * A subscriber that can be used to wrap around a regular subscriber (e.g. in the case of a lift()) which
 * masks onCompleted and onError signals and expose methods to manually set onCompleted or onError from
 * an external thread. The wrapped subscriber is wrapped in a SerializedSubscriber so usage is thread safe.
 *
 * @author David Liu
 */
public class SettableSubscriber<T> extends Subscriber<T> {
    private static final Logger logger = LoggerFactory.getLogger(SettableSubscriber.class);

    private final AtomicReference<Subscriber<? super T>> wrappedRef = new AtomicReference<>();

    public void setWrapped(Subscriber<? super T> subscriber) {
        wrappedRef.set(new SerializedSubscriber<>(subscriber));
    }

    public void setOnComplete() {
        Subscriber<? super T> wrapped = wrappedRef.get();
        if (wrapped != null && !wrapped.isUnsubscribed()) {
            wrapped.onCompleted();
            wrapped.unsubscribe();
        }
    }

    public void setOnError(Throwable e) {
        Subscriber<? super T> wrapped = wrappedRef.get();
        if (wrapped != null && !wrapped.isUnsubscribed()) {
            wrapped.onError(e);
            wrapped.unsubscribe();
        }
    }

    @Override
    public void onCompleted() {
        logger.info("Stream onCompleted, but not passing it on");
    }

    @Override
    public void onError(Throwable e) {
        logger.info("Stream onErrored, but not passing it on");
    }

    @Override
    public void onNext(T t) {
        Subscriber<? super T> wrapped = wrappedRef.get();
        if (wrapped != null && !wrapped.isUnsubscribed()) {
            wrapped.onNext(t);
        }
    }
}
