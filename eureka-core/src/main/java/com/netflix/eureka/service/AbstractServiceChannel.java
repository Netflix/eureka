package com.netflix.eureka.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Nitesh Kant
 */
public abstract class AbstractServiceChannel<STATE extends Enum> implements ServiceChannel {

    private static final Logger logger = LoggerFactory.getLogger(AbstractServiceChannel.class);

    protected static final IllegalStateException CHANNEL_CLOSED_EXCEPTION = new IllegalStateException("Channel is already closed.");

    protected final Subject<Void, Void> lifecycle;
    protected final AtomicReference<STATE> state;
    protected final Subscription heartbeatTickSubscription;
    protected final boolean heartbeatsEnabled;

    public AbstractServiceChannel(STATE initState) {
        state = new AtomicReference<STATE>(initState);
        lifecycle = ReplaySubject.create(); // Since its of type void there isn't any caching of data.
                                            // Its just the terminal state that is cached.
        heartbeatTickSubscription = Observable.empty().subscribe();
        heartbeatsEnabled = false;
    }

    protected AbstractServiceChannel(STATE initState, int heartbeatIntervalMs) {
        this(initState, heartbeatIntervalMs, Schedulers.computation());
    }

    protected AbstractServiceChannel(STATE initState, int heartbeatIntervalMs, Scheduler heartbeatCheckScheduler) {
        state = new AtomicReference<STATE>(initState);
        lifecycle = ReplaySubject.create(); // Since its of type void there isn't any caching of data.
                                            // Its just the terminal state that is cached.
        heartbeatsEnabled = true;
        heartbeatTickSubscription = Observable.interval(heartbeatIntervalMs, TimeUnit.MILLISECONDS,
                                                        heartbeatCheckScheduler)
                                              .subscribe(new HeartbeatChecker());
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return lifecycle;
    }

    @Override
    public final void close() {
        _close();
        heartbeatTickSubscription.unsubscribe(); // Unsubscribe is idempotent so even though it is called as a result
                                                 // of completion of this subscription, it is safe to call it here.
        lifecycle.onCompleted();
    }

    protected abstract void _close();

    protected abstract void onHeartbeatTick(long tickCount);

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

    private class HeartbeatChecker extends Subscriber<Long> {

        @Override
        public void onCompleted() {
            close();
        }

        @Override
        public void onError(Throwable e) {
            logger.error("Heartbeat checker subscription got an error. This will close this service channel.", e);
            close();
        }

        @Override
        public void onNext(Long aLong) {
            onHeartbeatTick(aLong);
        }
    }
}
