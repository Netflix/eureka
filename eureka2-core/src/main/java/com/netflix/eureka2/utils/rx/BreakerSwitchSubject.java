package com.netflix.eureka2.utils.rx;

import rx.Subscriber;
import rx.observers.SafeSubscriber;
import rx.subjects.Subject;

/**
 * A single type subject that can be breaked via the breakSwitch operator on close()
 *
 * @author David Liu
 */
public class BreakerSwitchSubject<T> extends Subject<T, T> {

    private final Subject<T, T> delegate;
    private final BreakerSwitchOperator<T> breaker;

    protected BreakerSwitchSubject(OnSubscribe<T> onSubscribe, Subject<T, T> delegate, BreakerSwitchOperator<T> breaker) {
        super(onSubscribe);
        this.delegate = delegate;
        this.breaker = breaker;
    }

    @Override
    public boolean hasObservers() {
        return delegate.hasObservers();
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }

    @Override
    public void onError(Throwable e) {
        delegate.onError(e);
    }

    @Override
    public void onNext(T t) {
        delegate.onNext(t);
    }

    public void close() {
        breaker.close();
    }

    public static <T> BreakerSwitchSubject<T> create(final Subject<T, T> delegate) {
        final BreakerSwitchOperator<T> breaker = new BreakerSwitchOperator<>();
        return new BreakerSwitchSubject<>(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                delegate.lift(breaker).subscribe(new SafeSubscriber<>(subscriber));
            }
        }, delegate, breaker);
    }
}
