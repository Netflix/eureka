package com.netflix.eureka.server.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.protocol.EurekaProtocolError;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.InterestRegistration;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.transport.ClientConnection;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

/**
 * An implementation of {@link InterestChannel} for eureka server.
 *
 * <b>This channel is self contained and does not require any external invocations on the {@link InterestChannel}
 * interface.</b>
 *
 * @author Nitesh Kant
 */
public class InterestChannelImpl extends AbstractChannel<InterestChannelImpl.STATES> implements InterestChannel {

    private static final IllegalStateException INTEREST_ALREADY_REGISTERED_EXCEPTION =
            new IllegalStateException("An interest is already registered. You must upgrade interest instead.");

    private static final IllegalStateException INTEREST_NOT_REGISTERED_EXCEPTION =
            new IllegalStateException("No interest is registered on this channel.");

    protected enum STATES {Idle, Registered, Closed}

    private final InterestNotificationMultiplexer notificationMultiplexer;

    public InterestChannelImpl(final EurekaRegistry registry, final ClientConnection transport) {
        super(STATES.Idle, transport, registry, 3, 30000);
        this.notificationMultiplexer = new InterestNotificationMultiplexer(registry);

        subscribeToTransportInput(new Action1<Object>() {
            @Override
            public void call(Object message) {
                /**
                 * Since, it is guaranteed that the messages on a connection are strictly sequential (single threaded
                 * invocation), we do not need to worry about thread safety here and hence we can safely do a
                 * isRegistered -> register kind of actions without worrying about race conditions.
                 */
                if (message instanceof InterestRegistration) {
                    Interest<InstanceInfo> interest = ((InterestRegistration) message).toComposite();
                    switch (state.get()) {
                        case Idle:
                            register(interest); // No need to subscribe, the register() call does the subscription.
                            break;
                        case Registered:
                            upgrade(interest); // No need to subscribe, the upgrade() call does the subscription.
                            break;
                        case Closed:
                            sendErrorOnTransport(CHANNEL_CLOSED_EXCEPTION);
                            break;
                    }
                } else if (message instanceof UnregisterInterestSet) {
                    switch (state.get()) {
                        case Idle:
                            sendErrorOnTransport(new IllegalStateException("No registration done before unregister."));
                            break;
                        case Registered:
                            unregister();// No need to subscribe, the unregister() call does the subscription.
                            break;
                        case Closed:
                            sendErrorOnTransport(CHANNEL_CLOSED_EXCEPTION);
                            break;
                    }
                } else if (message instanceof Heartbeat) {
                    heartbeat();
                } else {
                    sendErrorOnTransport(new EurekaProtocolError("Unexpected message " + message));
                }

            }
        });
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> register(Interest<InstanceInfo> interest) {
        logger.debug("Received intrest registration request {}", interest);

        if (!state.compareAndSet(STATES.Idle, STATES.Registered)) {// State check. Only register if the state is Idle.
            if (STATES.Closed == state.get()) {
                /**
                 * Since channel is already closed and hence the transport, we don't need to send an error on transport.
                 */
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            } else {
                sendErrorOnTransport(INTEREST_ALREADY_REGISTERED_EXCEPTION);
                return Observable.error(INTEREST_ALREADY_REGISTERED_EXCEPTION);
            }
        }


        sendAckOnTransport(); // Ack first since we registered with the registry. If the stream throws an error it is sent on the transport.

        notificationMultiplexer.changeNotifications().subscribe(
                new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        sendOnCompleteOnTransport(); // On complete of stream.
                        unregister();// No need to subscribe, the unregister() call does the subscription. Unregister to set the state properly.
                    }

                    @Override
                    public void onError(Throwable e) {
                        sendErrorOnTransport(e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        sendNotificationOnTransport(notification);
                    }
                });
        notificationMultiplexer.update(interest);

        return notificationMultiplexer.changeNotifications(); // It is not expected for caller (transport layer) to subscribe to this stream.
                                                              // This channel bridges EurekaRegistry and Transport.
    }

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        if (state.get() != STATES.Registered) {
            sendErrorOnTransport(INTEREST_NOT_REGISTERED_EXCEPTION);
            return Observable.error(INTEREST_NOT_REGISTERED_EXCEPTION);
        }

        notificationMultiplexer.update(newInterest);

        Observable<Void> toReturn = transport.sendAcknowledgment();
        subscribeToTransportSend(toReturn, "acknowledgment"); // Subscribe eagerly and not require the caller to subscribe.
        return toReturn;
    }

    @Override
    public Observable<Void> unregister() {
        if (!state.compareAndSet(STATES.Registered, STATES.Idle)) {
            if (state.get() == STATES.Closed) {
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            } else {
                sendErrorOnTransport(INTEREST_NOT_REGISTERED_EXCEPTION);
                return Observable.error(INTEREST_NOT_REGISTERED_EXCEPTION);
            }
        }

        notificationMultiplexer.unregister();

        Observable<Void> toReturn = transport.sendAcknowledgment();
        subscribeToTransportSend(toReturn, "acknowledgment"); // Subscribe eagerly and not require the caller to subscribe.
        return toReturn;
    }

    @Override
    public void _close() {
        state.set(STATES.Closed);
        notificationMultiplexer.unregister();
        super._close(); // Shutdown the transport
    }
}
