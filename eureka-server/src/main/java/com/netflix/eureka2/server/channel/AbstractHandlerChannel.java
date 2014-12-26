package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.AbstractServiceChannel;
import com.netflix.eureka2.interests.ChangeNotification;
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
 *
 * @author Nitesh Kant
 */
public abstract class AbstractHandlerChannel<STATE extends Enum> extends AbstractServiceChannel<STATE> {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractHandlerChannel.class);

    protected final MessageConnection transport;
    protected final SourcedEurekaRegistry<InstanceInfo> registry;

    protected AbstractHandlerChannel(STATE initState, MessageConnection transport, final SourcedEurekaRegistry<InstanceInfo> registry) {
        super(initState);
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

    protected <T> void sendOnTransport(T message) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending message on the transport: {}", message);
        }
        subscribeToTransportSend(transport.submit(message), message.getClass().getSimpleName());
    }

    protected void sendNotificationOnTransport(ChangeNotification<InstanceInfo> notification) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending change notification on the transport: {}", notification);
        }
        subscribeToTransportSend(transport.submit(notification), "notification");
    }

    protected void sendOnCompleteOnTransport() {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending onComplete on the transport.");
        }
        subscribeToTransportSend(transport.onCompleted(), "completion");
    }

    protected void sendErrorOnTransport(Throwable throwable) {
        if (logger.isErrorEnabled()) {
            logger.error("Sending error on the transport.", throwable);
        }
        subscribeToTransportSend(transport.onError(throwable), "error");
    }

    protected void sendAckOnTransport() {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending acknowledgment on the transport.");
        }
        subscribeToTransportSend(transport.acknowledge(), "acknowledgment");
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
