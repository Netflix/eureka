package com.netflix.eureka2.client.service;

import com.netflix.eureka2.client.transport.TransportClient;
import com.netflix.eureka2.service.AbstractServiceChannel;
import com.netflix.eureka2.service.ServiceChannel;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An abstract {@link ServiceChannel} implementation for common methods.
 *
 * @author Nitesh Kant
 */
public abstract class AbstractChannel<STATE extends Enum> extends AbstractServiceChannel<STATE> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractChannel.class);

    protected final TransportClient client;

    /**
     * There can only ever be one connection associated with a channel. This subject provides access to that connection
     * after a call is made to {@link #connect()}
     *
     * Why is this a {@link ReplaySubject}?
     *
     * Since there is always every a single connection created by this channel, everyone needs to get the same
     * connection. Now, the connection creation is lazy (in {@link #connect()} so we need a way to update this
     * {@link Observable}. Hence a {@link Subject} and one that replays the single connection created.
     */
    private ReplaySubject<MessageConnection> singleConnectionSubject;

    private volatile MessageConnection connectionIfConnected; // External callers should use "singleConnectionSubject"
    private final AtomicBoolean connectionRequestedOnce = new AtomicBoolean();

    protected AbstractChannel(final STATE initState, final TransportClient client) {
        super(initState);
        this.client = client;
        singleConnectionSubject = ReplaySubject.create();
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return lifecycle;
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
        if (connectionRequestedOnce.compareAndSet(false, true)) {
            return client.connect()
                    .take(1)
                    .map(new Func1<MessageConnection, MessageConnection>() {
                        @Override
                        public MessageConnection call(final MessageConnection serverConnection) {
                            // Guarded by the connection state, so it will only be invoked once.
                            connectionIfConnected = serverConnection;
                            singleConnectionSubject.onNext(serverConnection);
                            singleConnectionSubject.onCompleted();
                            return serverConnection;
                        }
                    });
        } else {
            return singleConnectionSubject;
        }

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
