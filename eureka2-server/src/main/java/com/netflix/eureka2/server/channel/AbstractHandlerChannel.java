package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.AbstractServiceChannel;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.metric.StateMachineMetrics;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

/**
 * An abstract {@link com.netflix.eureka2.channel.ServiceChannel} implementation for common methods.
 * All send* methods eagerly subscribes to the send result observable, and also returns the send result observable
 *
 * @author Nitesh Kant
 */
public abstract class AbstractHandlerChannel<STATE extends Enum<STATE>> extends AbstractServiceChannel<STATE> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractHandlerChannel.class);

    protected final MessageConnection transport;
    protected final SourcedEurekaRegistry<InstanceInfo> registry;


    protected AbstractHandlerChannel(STATE initState,
                                     MessageConnection transport,
                                     final SourcedEurekaRegistry<InstanceInfo> registry,
                                     StateMachineMetrics<STATE> metrics) {
        super(initState, metrics);
        this.transport = transport;
        this.registry = registry;
    }

    @Override
    protected void _close() {
        transport.shutdown(); // Idempotent so we can call it even if it is already shutdown.
    }

    protected void subscribeToTransportInput(final Action1<Object> onNext) {
        connectInputToLifecycle(transport.incoming(), onNext);
    }

    protected <T> Observable<Void> sendOnTransport(T message) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending message on the transport: {}", message);
        }
        return subscribeToTransportSend(transport.submit(message), message.getClass().getSimpleName());
    }

    protected Observable<Void> sendNotificationOnTransport(ChangeNotification<InstanceInfo> notification) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending change notification on the transport: {}", notification);
        }
        return subscribeToTransportSend(transport.submit(notification), "notification");
    }

    protected Observable<Void> sendOnCompleteOnTransport() {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending onComplete on the transport.");
        }
        return subscribeToTransportSend(transport.onCompleted(), "completion");
    }

    protected Observable<Void> sendErrorOnTransport(Throwable throwable) {
        if (logger.isErrorEnabled()) {
            logger.error("Sending error on the transport.", throwable);
        }
        return subscribeToTransportSend(transport.onError(throwable), "error");
    }

    protected Observable<Void> sendAckOnTransport() {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending acknowledgment on the transport.");
        }
        return subscribeToTransportSend(transport.acknowledge(), "acknowledgment");
    }

    /**
     * Eagerly subscribe to the transportSend, and return the transportSendResult
     */
    protected Observable<Void> subscribeToTransportSend(Observable<Void> transportSendResult, final String sendType) {
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

        return transportSendResult;
    }
}
