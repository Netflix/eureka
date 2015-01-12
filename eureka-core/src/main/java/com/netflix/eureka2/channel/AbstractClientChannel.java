package com.netflix.eureka2.channel;

import com.netflix.eureka2.transport.TransportClient;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * An abstract {@link ServiceChannel} implementation for common methods.
 *
 * @author Nitesh Kant
 */
public abstract class AbstractClientChannel<STATE extends Enum> extends AbstractServiceChannel<STATE> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractClientChannel.class);

    protected final TransportClient client;

    private volatile MessageConnection connectionIfConnected;

    private final Observable<MessageConnection> singleConnection;

    protected AbstractClientChannel(final STATE initState, final TransportClient client) {
        super(initState);
        this.client = client;

        singleConnection = client.connect()
                .take(1)
                .map(new Func1<MessageConnection, MessageConnection>() {
                    @Override
                    public MessageConnection call(MessageConnection serverConnection) {
                        connectionIfConnected = serverConnection;
                        return connectionIfConnected;
                    }
                })
                .replay()
                .refCount();
    }

    @Override
    protected void _close() {
        if (logger.isDebugEnabled()) {
            logger.debug("Closing client interest channel with state: " + state.get());
        }

        if (null != connectionIfConnected) {
            connectionIfConnected.shutdown();
        }
    }

    /**
     * Idempotent method that returns the one and only connection associated with this channel.
     *
     * @return The one and only connection associated with this channel.
     */
    protected Observable<MessageConnection> connect() {
        return singleConnection;
    }

    protected void sendErrorOnConnection(MessageConnection connection, Throwable throwable) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending error to the server.", throwable);
        }
        subscribeToTransportSend(connection.onError(throwable), "error");
    }

    protected void sendAckOnConnection(MessageConnection connection) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending acknowledgment to the server.");
        }
        subscribeToTransportSend(connection.acknowledge(), "acknowledgment");
    }

    protected void subscribeToTransportSend(Observable<Void> sendResult, final String sendType) {
        sendResult.subscribe(new Action1<Void>() {
            @Override
            public void call(Void aVoid) {
                // Nothing to do for a void.
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                logger.warn("Failed to send " + sendType + " to the server. Closing the channel.", throwable);
                close();
            }
        });
    }
}
