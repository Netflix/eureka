package com.netflix.eureka2.channel;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.metric.StateMachineMetrics;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

/**
 * @author Nitesh Kant
 */
public abstract class AbstractServiceChannel<STATE extends Enum<STATE>> implements ServiceChannel {

    protected static final IllegalStateException CHANNEL_CLOSED_EXCEPTION = new IllegalStateException("Channel is already closed.");

    // Channel descriptive name to be used in the log file - that should come from channel API
    protected final String name = getClass().getSimpleName();

    protected final Subject<Void, Void> lifecycle;

    protected final AtomicReference<STATE> state;
    private final STATE initState;
    private final StateMachineMetrics<STATE> metrics;

    protected AbstractServiceChannel(STATE initState, StateMachineMetrics<STATE> metrics) {
        this.initState = initState;
        this.metrics = metrics;
        this.state = new AtomicReference<>(initState);
        // Since its of type void there isn't any caching of data. Its just the terminal state that is cached.
        this.lifecycle = ReplaySubject.create();
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

    protected boolean moveToState(STATE from, STATE to) {
        if (state.compareAndSet(from, to)) {
            if (metrics != null) {
                // We do not track initState (==idle), only subsequent states that
                // happen when a connection is established.
                if (from == initState) {
                    metrics.incrementStateCounter(to);
                } else {
                    metrics.stateTransition(from, to);
                }
            }
            return true;
        }
        return false;
    }

    protected boolean moveToState(STATE to) {
        if (state.get() == to) {
            return false;
        }
        return moveToState(state.get(), to);
    }
}
