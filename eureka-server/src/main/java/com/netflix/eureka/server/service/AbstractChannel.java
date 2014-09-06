package com.netflix.eureka.server.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.transport.ClientConnection;
import com.netflix.eureka.service.AbstractServiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An abstract {@link com.netflix.eureka.service.ServiceChannel} implementation for common methods.
 *
 * @author Nitesh Kant
 */
public abstract class AbstractChannel<STATE extends Enum> extends AbstractServiceChannel<STATE> {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractChannel.class);

    protected final ClientConnection transport;
    protected final EurekaRegistry registry;

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

    private final int maxMissedHeartbeats;

    protected AbstractChannel(STATE initState, ClientConnection transport, final EurekaRegistry registry) {
        super(initState);
        this.transport = transport;
        this.registry = registry;
        // No Heartbeats.
        maxMissedHeartbeats = Integer.MAX_VALUE;
    }

    protected AbstractChannel(STATE initState, ClientConnection transport, final EurekaRegistry registry,
                              int maxMissedHeartbeats, int heartbeatIntervalMs) {
        this(initState, transport, registry, maxMissedHeartbeats, heartbeatIntervalMs, Schedulers.computation());
    }

    protected AbstractChannel(STATE initState, ClientConnection transport, final EurekaRegistry registry,
                              int maxMissedHeartbeats, int heartbeatIntervalMs, Scheduler heartbeatCheckScheduler) {
        super(initState, heartbeatIntervalMs);
        this.transport = transport;
        this.registry = registry;
        this.maxMissedHeartbeats = maxMissedHeartbeats;
    }

    @Override
    public void heartbeat() {
        if (heartbeatsEnabled) {
            int missedHeartbeats = missingHeartbeatsCount.decrementAndGet();// See javadoc of this field.
            correctMissedHeartbeatLowerBound(missedHeartbeats);
        }
    }

    @Override
    protected void onHeartbeatTick(long tickCount) {
        int missedHeartbeats = missingHeartbeatsCount.incrementAndGet();
        if (missedHeartbeats >= maxMissedHeartbeats) {
            onHeartbeatExpiry();
        }
    }

    @Override
    protected void _close() {
        transport.shutdown(); // Idempotent so we can call it even if it is already shutdown.

    }

    protected final void onHeartbeatExpiry() {
        close();
    }

    private void correctMissedHeartbeatLowerBound(int missedHeartbeats) {
        /**
         * Tries till the lower bound is corrected either externally (by heartbeat misses) or by this thread.
         */
        if (missedHeartbeats < -1) {
            while (!missingHeartbeatsCount.compareAndSet(missedHeartbeats, -1)) {
                correctMissedHeartbeatLowerBound(missingHeartbeatsCount.get());
            }
        }
    }

    protected void subscribeToTransportInput(final Action1<Object> onNext) {
        connectInputToLifecycle(transport.getInput(), onNext);
    }

    protected void sendNotificationOnTransport(ChangeNotification<InstanceInfo> notification) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending change notification on the transport: {}", notification);
        }
        subscribeToTransportSend(transport.sendNotification(notification), "notification");
    }

    protected void sendOnCompleteOnTransport() {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending onComplete on the transport.");
        }
        subscribeToTransportSend(transport.sendOnComplete(), "completion");
    }

    protected void sendErrorOnTransport(Throwable throwable) {
        if (logger.isErrorEnabled()) {
            logger.error("Sending error on the transport.", throwable);
        }
        subscribeToTransportSend(transport.sendError(throwable), "error");
    }

    protected void sendAckOnTransport() {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending acknowledgment on the transport.");
        }
        subscribeToTransportSend(transport.sendAcknowledgment(), "acknowledgment");
    }

    protected void subscribeToTransportSend(Observable<Void> transportSendResult, final String sendType) {
        transportSendResult.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.debug("Sent successfully message of type " + sendType);
            }

            @Override
            public void onError(Throwable throwable) {
                logger.warn("Failed to send " + sendType + " on the transport. Closing the channel.", throwable);
                close();
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });
    }
}
