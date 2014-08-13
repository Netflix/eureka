package com.netflix.eureka.server.service;

import com.netflix.eureka.service.ServiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An abstract {@link com.netflix.eureka.service.ServiceChannel} implementation for common methods.
 *
 * @author Nitesh Kant
 */
public abstract class AbstractChannel implements ServiceChannel {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractChannel.class);

    /**
     * For every heartbeat received, this counter is decremented by 1 and for every hearbeat check tick it is
     * incremented by 1.
     * At steady state, this should be -1, 0 or 1 depending on which thread is faster i.e. heartbeat or heartbeat
     * checker.
     * When the heartbeats are missed, the counter increases and when it becomes >= max tolerable mixed heartbeat, the
     * channel is notified via {@link #onHeartbeatExpiry()} and then closed.
     *
     * Edge Cases:
     *
     * 1) Heartbeat checker does not tick: The counter will become a high negative number and hence the tolerance for
     * missing heartbeats will increase. {@link #heartbeat()} checks and corrects this lower bound.
     * 2) Heartbeat checker terminates with error: This will terminate the channel, if not already terminated.
     */
    private final AtomicInteger missingHeartbeatsCount = new AtomicInteger();

    private final boolean heartbeatsEnabled;
    private final int maxMissedHeartbeats;
    private final Subscription heartbeatTickSubscription;

    protected AbstractChannel() {
        // No Heartbeats.
        maxMissedHeartbeats = Integer.MAX_VALUE;
        heartbeatTickSubscription = Observable.empty().subscribe();
        heartbeatsEnabled = false;
    }

    protected AbstractChannel(int maxMissedHeartbeats, int heartbeatIntervalMs) {
        this(maxMissedHeartbeats, heartbeatIntervalMs, Schedulers.computation());
    }

    protected AbstractChannel(int maxMissedHeartbeats, int heartbeatIntervalMs, Scheduler heartbeatCheckScheduler) {
        this.maxMissedHeartbeats = maxMissedHeartbeats;
        heartbeatsEnabled = true;
        heartbeatTickSubscription = Observable.interval(heartbeatIntervalMs, TimeUnit.MILLISECONDS,
                                                        heartbeatCheckScheduler)
                                              .subscribe(new HeartbeatChecker());
    }

    @Override
    public void heartbeat() {
        if (heartbeatsEnabled) {
            int missedHeartbeats = missingHeartbeatsCount.decrementAndGet();// See javadoc of this field.
            correctMissedHeartbeatLowerBound(missedHeartbeats);
        }
    }

    @Override
    public final void close() {
        _close();
        heartbeatTickSubscription.unsubscribe(); // Unsubscribe is idempotent so even though it is called as a result
                                                 // of completion of this subscription, it is safe to call it here.
    }

    protected void _close() {
    }

    protected final void onHeartbeatExpiry() {
        _onHeartbeatExpiry();
        close();
    }

    protected void _onHeartbeatExpiry() {
    }

    private void correctMissedHeartbeatLowerBound(int missedHeatbeats) {
        /**
         * Tries till the lower bound is corrected either externally (by heartbeat misses) or by this thread.
         */
        if (missedHeatbeats < -1) {
            while (!missingHeartbeatsCount.compareAndSet(missedHeatbeats, -1)) {
                correctMissedHeartbeatLowerBound(missingHeartbeatsCount.get());
            }
        }
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
            int missedHeartbeats = missingHeartbeatsCount.incrementAndGet();
            if (missedHeartbeats >= maxMissedHeartbeats) {
                onHeartbeatExpiry();
            }
        }
    }
}
