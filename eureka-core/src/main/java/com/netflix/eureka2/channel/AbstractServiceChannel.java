package com.netflix.eureka2.channel;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Nitesh Kant
 */
public abstract class AbstractServiceChannel<STATE extends Enum> implements ServiceChannel {

    protected static final IllegalStateException CHANNEL_CLOSED_EXCEPTION = new IllegalStateException("Channel is already closed.");

    protected final Subject<Void, Void> lifecycle;
    protected final AtomicReference<STATE> state;

    public AbstractServiceChannel(STATE initState) {
        state = new AtomicReference<STATE>(initState);
        lifecycle = ReplaySubject.create(); // Since its of type void there isn't any caching of data.
        // Its just the terminal state that is cached.
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return lifecycle;
    }

    @Override
    public final void close() {
        _close();
        lifecycle.onCompleted();
    }

    protected abstract void _close();

    protected <T> void connectInputToLifecycle(Observable<T> inputObservable, final Action1<T> onNext) {
        inputObservable.subscribe(new Subscriber<T>() {
            @Override
            public void onCompleted() {
                close();
                lifecycle.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                close();
                lifecycle.onError(e);
            }

            @Override
            public void onNext(T message) {
                onNext.call(message);
            }
        });
    }
}
