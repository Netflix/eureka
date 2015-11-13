package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.channel.InterestChannel.STATE;
import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics;
import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics.ChannelSubscriptionMonitor;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ModifyNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.spi.protocol.EurekaProtocolError;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.spi.protocol.interest.InterestRegistration;
import com.netflix.eureka2.spi.protocol.interest.UnregisterInterestSet;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * An implementation of {@link InterestChannel} for eureka server.
 *
 * <b>This channel is self contained and does not require any external invocations on the {@link InterestChannel}
 * interface.</b>
 *
 * @author Nitesh Kant
 */
public class InterestChannelImpl extends AbstractHandlerChannel<STATE> implements InterestChannel {

    private static final Logger logger = LoggerFactory.getLogger(InterestChannelImpl.class);

    private final Subscriber<Void> channelSubscriber;
    private final Source selfSource;
    private final ServerInterestChannelMetrics metrics;

    private final EurekaRegistryView<InstanceInfo> registryView;  // keep reference of for IDE debugging
    private final InterestNotificationMultiplexer notificationMultiplexer;
    private final ChannelSubscriptionMonitor channelSubscriptionMonitor;

    public InterestChannelImpl(final EurekaRegistryView<InstanceInfo> registry, final EurekaConnection transport, final ServerInterestChannelMetrics metrics) {
        super(STATE.Idle, transport, metrics);
        this.metrics = metrics;
        this.registryView = registry;
        this.notificationMultiplexer = new InterestNotificationMultiplexer(registry);
        this.channelSubscriptionMonitor = new ChannelSubscriptionMonitor(metrics);
        this.selfSource = InstanceModel.getDefaultModel().createSource(Source.Origin.INTERESTED, "serverInterestChannel");
        this.channelSubscriber = new LoggingSubscriber<>(logger, "channel");

        connectInputToLifecycle(transport.incoming())
                .doOnNext(new Action1<Object>() {
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
                                    change(Interests.forNone());  // best effort acknowledge back
                                    close();
                                    break;
                                case Closed:
                                    sendErrorOnTransport(CHANNEL_CLOSED_EXCEPTION);
                                    break;
                            }
                        } else {
                            sendErrorOnTransport(new EurekaProtocolError("Unexpected message " + message));
                        }
                    }
                })
                .ignoreElements()
                .cast(Void.class)
                .subscribe(channelSubscriber);
    }

    @Override
    public Observable<Void> change(Interest<InstanceInfo> newInterest) {
        logger.debug("Received interest change request {}", newInterest);

        if (STATE.Closed == state.get()) {
            /**
             * Since channel is already closed and hence the transport, we don't need to send an error on transport.
             */
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }

        if (moveToState(STATE.Idle, STATE.Open)) {
            initializeNotificationMultiplexer();
        }

        // We acknowledge before subscription, as subscribing may generate notification stream
        // that will be delivered prior this subscription is acknowledged.
        Observable<Void> toReturn = transport.acknowledge();
        subscribeToTransportSend(toReturn, "acknowledgment"); // Subscribe eagerly and not require the caller to subscribe.

        channelSubscriptionMonitor.update(newInterest);
        notificationMultiplexer.update(newInterest);

        return toReturn;
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> getChangeNotificationStream() {
        return Observable.error(new UnsupportedOperationException("not implemented on server side interest channel"));
    }

    private void initializeNotificationMultiplexer() {
        notificationMultiplexer.changeNotifications()
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<Void>>() { // TODO concatMap once backpressure is properly working
                    @Override
                    public Observable<Void> call(ChangeNotification<InstanceInfo> notification) {
                        if (logger.isDebugEnabled()) {
                            if (notification.isDataNotification()) {
                                logger.debug("Sending notification of kind {} for instance {} to the client", notification.getKind(), notification.getData().getId());
                            } else {
                                StreamStateNotification<InstanceInfo> streamStateNotification = (StreamStateNotification<InstanceInfo>) notification;
                                logger.debug("Sending buffer sentinel {} to the client", streamStateNotification.getBufferState());
                            }
                        }
                        return handleChangeNotification(notification);
                    }
                })
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        sendOnCompleteOnTransport(); // On complete of stream.
                        // there is no need to call change(forNone) here as when this onComplete, the channel would
                        // already be in state Closed, and hence will no longer be accepting change() calls.
                    }

                    @Override
                    public void onError(Throwable e) {
                        sendErrorOnTransport(e);
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // no-op
                    }
                });
    }

    protected Observable<Void> handleChangeNotification(ChangeNotification<InstanceInfo> notification) {
        if (notification.isDataNotification()) {
            metrics.incrementApplicationNotificationCounter(notification.getData().getApp());
        }
        Observable<Void> sendResult = sendNotification(notification);
        if (sendResult != null) {
            return subscribeToTransportSend(sendResult, "notification");
        } else {
            // TODO: how to report effectively invariant violations that should never happen in a valid code, but are not errors?
            logger.warn("No-effect modify in the interest channel: {}", notification);
            return Observable.empty();
        }
    }

    private Observable<Void> sendNotification(ChangeNotification<InstanceInfo> notification) {
        switch (notification.getKind()) {
            case Add:
                return transport.submitWithAck(ProtocolModel.getDefaultModel().newAddInstance(notification.getData()));
            case Delete:
                return transport.submitWithAck(ProtocolModel.getDefaultModel().newDeleteInstance(notification.getData().getId()));
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
                        toReturn = transport.submitWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(delta));
                    } else {
                        toReturn.concatWith(transport.submitWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(delta)));
                    }
                }
                return toReturn;
            case BufferSentinel:
                return transport.submitWithAck(ProtocolModel.getDefaultModel().newStreamStateUpdate((StreamStateNotification<InstanceInfo>) notification));
        }
        return Observable.error(new IllegalArgumentException("Unknown change notification type: " +
                notification.getKind()));
    }

    @Override
    public void _close() {
        if (moveToState(STATE.Open, STATE.Closed)) {
            channelSubscriber.unsubscribe();
            channelSubscriptionMonitor.update(Interests.forNone());
            notificationMultiplexer.unregister();
            super._close(); // Shutdown the transport
        }
    }

    @Override
    public Source getSource() {
        return selfSource;
    }
}
