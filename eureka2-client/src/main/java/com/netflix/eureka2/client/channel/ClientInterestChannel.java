package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.AbstractClientChannel;
import com.netflix.eureka2.channel.ChannelFunctions;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.protocol.common.InterestSetNotification;
import com.netflix.eureka2.protocol.interest.InterestRegistration;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David Liu
 */
public class ClientInterestChannel extends AbstractClientChannel<InterestChannel.STATE> implements InterestChannel {

    private static final Logger logger = LoggerFactory.getLogger(ClientInterestChannel.class);

    private final ChannelFunctions channelFunctions = new ChannelFunctions(logger);

    /**
     * Since we assume single threaded access to this channel, no need for concurrency control
     */
    protected Observable<ChangeNotification<InstanceInfo>> channelInterestStream;

    private final EurekaRegistry<InstanceInfo> registry;
    private final Subscriber<Void> channelSubscriber;

    private final Source selfSource;

    /**
     * A local copy of instances received by this channel from the server. This is used for:
     *
     * <ul>
     <li><i>Updates on the wire</i>: Since we only get the delta on the wire, we use this map to get the last seen
     {@link InstanceInfo} and apply the delta on it to get the new {@link InstanceInfo}</li>
     <li><i>Deletes on the wire</i>: Since we only get the identifier for the instance deleted, we use this map to
     get the last seen {@link InstanceInfo}</li>
     </ul>
     *
     * <h2>Thread safety</h2>
     *
     * Since this channel directly leverages the underlying {@link com.netflix.eureka2.transport.MessageConnection} and our underlying stack guarantees
     * that there are not concurrent updates sent to the input reader, we can safely assume that this code is single
     * threaded.
     */
    private final Map<String, InstanceInfo> idVsInstance = new HashMap<>();

    public ClientInterestChannel(EurekaRegistry<InstanceInfo> registry,
                                 TransportClient client,
                                 long generationId,
                                 InterestChannelMetrics metrics) {
        super(STATE.Idle, client, metrics);
        this.selfSource = new Source(Source.Origin.INTERESTED, "clientInterestChannel", generationId);
        this.registry = registry;
        this.channelSubscriber = new LoggingSubscriber<>(logger, "channel");
        this.channelInterestStream = createInterestStream();

        Observable<Void> evictionOb = channelFunctions.setUpPrevChannelEviction(selfSource, registry);
        evictionOb.subscribe(new LoggingSubscriber<Void>(logger, "eviction"));

        logger.info("created new {} with source {}", this.getClass().getSimpleName(), selfSource);
    }

    @Override
    public Source getSource() {
        return selfSource;
    }

    // channel contract means this will be invoked in serial.
    @Override
    public Observable<Void> change(final Interest<InstanceInfo> newInterest) {
        if (state.get() == STATE.Closed) {
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }

        Observable<Void> serverRequest = connect() // Connect is idempotent and does not connect on every call.
                .switchMap(new Func1<MessageConnection, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(MessageConnection serverConnection) {
                        return sendExpectAckOnConnection(serverConnection, new InterestRegistration(newInterest));
                    }
                });

        return Observable
                .create(new Observable.OnSubscribe<Void>() {
                    @Override
                    public void call(Subscriber<? super Void> subscriber) {
                        if (STATE.Closed == state.get()) {
                            subscriber.onError(CHANNEL_CLOSED_EXCEPTION);
                        } else if (moveToState(STATE.Idle, STATE.Open)) {
                            logger.debug("First time registration: {}", newInterest);
                            registry.connect(selfSource, channelInterestStream).subscribe(channelSubscriber);
                        } else {
                            logger.debug("Channel changes: {}", newInterest);
                        }
                        subscriber.onCompleted();
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.warn("Error connecting interestChannel to registry", throwable);
                    }
                })
                .concatWith(serverRequest);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> getChangeNotificationStream() {
        return channelInterestStream;
    }

    @Override
    protected void _close() {
        STATE from = moveToState(STATE.Closed);
        if (from != STATE.Closed) {  // if this is the first/only close
            channelSubscriber.unsubscribe();
            idVsInstance.clear();
            super._close();
        }
    }

    protected Observable<ChangeNotification<InstanceInfo>> createInterestStream() {

        return connect().switchMap(new Func1<MessageConnection, Observable<? extends ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<? extends ChangeNotification<InstanceInfo>> call(final MessageConnection connection) {
                return connection.incoming()
                        .filter(new Func1<Object, Boolean>() {
                            @Override
                            public Boolean call(Object message) {
                                boolean isKnown = message instanceof InterestSetNotification;
                                if (!isKnown) {
                                    logger.warn("Unrecognized discovery protocol message of type " + message.getClass());
                                }
                                return isKnown;
                            }
                        })
                        .cast(InterestSetNotification.class)
                        .concatMap(new Func1<InterestSetNotification, Observable<ChangeNotification<InstanceInfo>>>() {
                            @Override
                            public Observable<ChangeNotification<InstanceInfo>> call(InterestSetNotification message) {
                                ChangeNotification<InstanceInfo> changeNotification = channelFunctions
                                        .channelMessageToNotification(message, selfSource, idVsInstance);

                                if (changeNotification != null) {
                                    Observable toReturn = sendAckOnConnection(connection)
                                            .cast(ChangeNotification.class)
                                            .concatWith(Observable.just(changeNotification));

                                    return toReturn;
                                } else {
                                    return Observable.empty();  // can only happen if we get a no-op or an unrecognised message, so do nothing
                                }
                            }
                        })
                        .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                            @Override
                            public Boolean call(ChangeNotification<InstanceInfo> notification) {
                                return null != notification;
                            }
                        });
            }
        }).share();
    }
}
