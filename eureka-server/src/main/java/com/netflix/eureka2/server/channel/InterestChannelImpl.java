package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.DeleteInstance;
import com.netflix.eureka2.protocol.discovery.InterestRegistration;
import com.netflix.eureka2.protocol.discovery.SnapshotComplete;
import com.netflix.eureka2.protocol.discovery.SnapshotRegistration;
import com.netflix.eureka2.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka2.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.InterestChannelImpl.STATES;
import com.netflix.eureka2.server.metric.InterestChannelMetrics;
import com.netflix.eureka2.server.metric.InterestChannelMetrics.ChannelSubscriptionMonitor;
import com.netflix.eureka2.transport.MessageConnection;
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
public class InterestChannelImpl extends AbstractHandlerChannel<STATES> implements InterestChannel {

    private static final Exception SNAPSHOT_REQUEST_NOT_ALLOWED = new Exception("Snapshot subscription not allowed on active channel");

    public enum STATES {Idle, Open, Closed}

    private final InterestChannelMetrics metrics;

    private final InterestNotificationMultiplexer notificationMultiplexer;
    private final ChannelSubscriptionMonitor channelSubscriptionMonitor;

    public InterestChannelImpl(final SourcedEurekaRegistry<InstanceInfo> registry, final MessageConnection transport, final InterestChannelMetrics metrics) {
        super(STATES.Idle, transport, registry);
        this.metrics = metrics;
        this.metrics.incrementStateCounter(STATES.Idle);
        this.notificationMultiplexer = new InterestNotificationMultiplexer(registry);
        this.channelSubscriptionMonitor = new ChannelSubscriptionMonitor(metrics);

        subscribeToTransportInput(new Action1<Object>() {
            @Override
            public void call(Object message) {
                /**
                 * Since, it is guaranteed that the messages on a connection are strictly sequential (single threaded
                 * invocation), we do not need to worry about thread safety here and hence we can safely do a
                 * isRegistered -> register kind of actions without worrying about race conditions.
                 */
                if (message instanceof SnapshotRegistration) {
                    switch (state.get()) {
                        case Idle:
                            sendSnapshot(((SnapshotRegistration) message).getInterests());
                            state.set(STATES.Closed);
                            metrics.stateTransition(STATES.Idle, STATES.Open);
                            break;
                        case Open:
                            sendErrorOnTransport(SNAPSHOT_REQUEST_NOT_ALLOWED);
                            break;
                        case Closed:
                            sendErrorOnTransport(CHANNEL_CLOSED_EXCEPTION);
                            break;
                    }
                } else if (message instanceof InterestRegistration) {
                    Interest<InstanceInfo> interest = ((InterestRegistration) message).toComposite();
                    switch (state.get()) {
                        case Idle:
                        case Open:
                            change(interest);
                            break;
                        case Closed:
                            sendErrorOnTransport(CHANNEL_CLOSED_EXCEPTION);
                            break;
                    }
                } else if (message instanceof UnregisterInterestSet) {
                    switch (state.get()) {
                        case Idle:
                        case Open:
                            change(Interests.forNone());
                            break;
                        case Closed:
                            sendErrorOnTransport(CHANNEL_CLOSED_EXCEPTION);
                            break;
                    }
                } else {
                    sendErrorOnTransport(new EurekaProtocolError("Unexpected message " + message));
                }
            }
        });
    }

    private void sendSnapshot(Interest<InstanceInfo>[] interests) {
        Observable<Void> toReturn = transport.acknowledge();
        subscribeToTransportSend(toReturn, "acknowledgment(SnapshotRegistration)");

        registry.forSnapshot(new MultipleInterests<InstanceInfo>(interests)).subscribe(new Subscriber<InstanceInfo>() {
            @Override
            public void onCompleted() {
                logger.debug("Snapshot stream from registry completed. Sending SnapshotComplete to the client");
                Observable<Void> sendResult = transport.submit(SnapshotComplete.INSTANCE);
                subscribeToTransportSend(sendResult, "snapshotCompleted");
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Snapshot subscription stream terminated due to an error", e);
            }

            @Override
            public void onNext(InstanceInfo instanceInfo) {
                handleChangeNotification(new ChangeNotification<InstanceInfo>(Kind.Add, instanceInfo));
            }
        });
    }

    @Override
    public Observable<Void> change(Interest<InstanceInfo> newInterest) {
        logger.debug("Received interest change request {}", newInterest);

        if (STATES.Closed == state.get()) {
            /**
             * Since channel is already closed and hence the transport, we don't need to send an error on transport.
             */
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }
        if (state.compareAndSet(STATES.Idle, STATES.Open)) {
            metrics.stateTransition(STATES.Idle, STATES.Open);
            initializeNotificationMultiplexer();
        }

        channelSubscriptionMonitor.update(newInterest);
        notificationMultiplexer.update(newInterest);

        Observable<Void> toReturn = transport.acknowledge();
        subscribeToTransportSend(toReturn, "acknowledgment"); // Subscribe eagerly and not require the caller to subscribe.
        return toReturn;
    }

    private void initializeNotificationMultiplexer() {
        notificationMultiplexer.changeNotifications().subscribe(
                new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        sendOnCompleteOnTransport(); // On complete of stream.
                        change(Interests.forNone());  // TODO: needed?
                    }

                    @Override
                    public void onError(Throwable e) {
                        sendErrorOnTransport(e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        handleChangeNotification(notification);
                    }
                });
    }

    protected void handleChangeNotification(ChangeNotification<InstanceInfo> notification) {
        metrics.incrementApplicationNotificationCounter(notification.getData().getApp());
        Observable<Void> sendResult = sendNotification(notification);
        if (sendResult != null) {
            subscribeToTransportSend(sendResult, "notification");
        } else {
            // TODO: how to report effectively invariant violations that should never happen in a valid code, but are not errors?
            logger.warn("No-effect modify in the interest channel: {}", notification);
        }
    }

    private Observable<Void> sendNotification(ChangeNotification<InstanceInfo> notification) {
        switch (notification.getKind()) {
            case Add:
                return transport.submitWithAck(new AddInstance(notification.getData()));
            case Delete:
                return transport.submitWithAck(new DeleteInstance(notification.getData().getId()));
            case Modify:
                final ModifyNotification<InstanceInfo> modifyNotification = (ModifyNotification<InstanceInfo>) notification;

                /**
                 * Below will only work correctly if {@link MessageBroker#submitWithAck(Object)} is a lazy submit i.e.
                 * the message is only sent over the wire when subscribed. If it is eager i.e. the message is written
                 * to the underlying connection without subscription then {@link Observable#concatWith(Observable)}
                 * will eagerly write all the messages without waiting for an ack.
                 */
                Observable<Void> toReturn = null;
                for (final Delta<?> delta : modifyNotification.getDelta()) {
                    if (null == toReturn) {
                        toReturn = transport.submitWithAck(new UpdateInstanceInfo(delta));
                    } else {
                        toReturn.concatWith(transport.submitWithAck(new UpdateInstanceInfo(delta)));
                    }
                }
                return toReturn;
        }
        return Observable.error(new IllegalArgumentException("Unknown change notification type: " +
                notification.getKind()));
    }

    @Override
    public void _close() {
        if (state.compareAndSet(STATES.Open, STATES.Closed)) {
            state.set(STATES.Closed);
            channelSubscriptionMonitor.update(Interests.forNone());
            metrics.stateTransition(STATES.Open, STATES.Closed);
            notificationMultiplexer.unregister();
            super._close(); // Shutdown the transport
        }
    }
}
