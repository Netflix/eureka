package com.netflix.eureka.client.service;

import com.netflix.eureka.service.ServiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;

/**
 * An abstract {@link com.netflix.eureka.service.ServiceChannel} implementation for common methods.
 *
 * @author Nitesh Kant
 */
public abstract class AbstractChannel implements ServiceChannel {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractChannel.class);

    private final Subscription heartbeatTickSubscription;
    private final boolean heartbeatsEnabled;

    protected AbstractChannel() {
        // No Heartbeats.
        heartbeatTickSubscription = Observable.empty().subscribe();
        heartbeatsEnabled = false;
    }

    protected AbstractChannel(int heartbeatIntervalMs) {
        this(heartbeatIntervalMs, Schedulers.computation());
    }

    protected AbstractChannel(int heartbeatIntervalMs, Scheduler heartbeatCheckScheduler) {
        heartbeatsEnabled = true;
        heartbeatTickSubscription = Observable.interval(heartbeatIntervalMs, TimeUnit.MILLISECONDS,
                                                        heartbeatCheckScheduler)
                                              .subscribe(new HeartbeatSender());
    }

    @Override
    public final void close() {
        _close();
        heartbeatTickSubscription.unsubscribe(); // Unsubscribe is idempotent so even though it is called as a result
                                                 // of completion of this subscription, it is safe to call it here.
    }

    protected void _close() {
    }

    private class HeartbeatSender extends Subscriber<Long> {

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
            if (heartbeatsEnabled) {
                heartbeat();
            }
        }
    }
}
