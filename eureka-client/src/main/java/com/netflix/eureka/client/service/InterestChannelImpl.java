package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.ServerConnection;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.ModifyNotification;
import com.netflix.eureka.interests.MultipleInterests;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.InterestRegistration;
import com.netflix.eureka.protocol.discovery.InterestSetNotification;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.observers.SafeSubscriber;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@link InterestChannel}. It is mandatory that all operations
 * on the channel are serialized, by the external client. This class is not thread safe and all operations on it
 * shall be executed by the same thread.
 *
 * Use {@link InterestChannelInvoker} for serializing operations on this channel.
 *
 * @author Nitesh Kant
 */
/*pkg-private: Used by EurekaClientService only*/class InterestChannelImpl
        extends AbstractChannel<InterestChannelImpl.STATES> implements ClientInterestChannel {

    private static final Logger logger = LoggerFactory.getLogger(InterestChannelImpl.class);

    private static final IllegalStateException INTEREST_NOT_REGISTERED_EXCEPTION =
            new IllegalStateException("No interest is registered on this channel.");

    /**
     * Since we assume single threaded access to this channel, no need for concurrency control
     */
    protected MultipleInterests<InstanceInfo> channelInterest;
    protected Observable<ChangeNotification<InstanceInfo>> channelInterestStream;

    protected Subscriber<ChangeNotification<InstanceInfo>> channelInterestSubscriber;

    protected enum STATES {Idle, Open, Closed}

    protected EurekaRegistry<InstanceInfo> registry;

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
     * Since this channel directly leverages the underlying {@link ServerConnection} and our underlying stack guarantees
     * that there are not concurrent updates sent to the input reader, we can safely assume that this code is single
     * threaded.
     */
    private final Map<String, InstanceInfo> idVsInstance = new HashMap<>();

    public InterestChannelImpl(final EurekaRegistry<InstanceInfo> registry, TransportClient client) {
        super(STATES.Idle, client, 30000);
        this.registry = registry;
        channelInterest = new MultipleInterests<>();  // blank channelInterest to start with
        channelInterestSubscriber = new ChannelInterestSubscriber(registry);
        channelInterestStream = createInterestStream();
    }

    // channel contract means this will be invoked in serial.
    @Override
    public Observable<Void> change(final Interest<InstanceInfo> newInterest) {
        Observable<Void> serverRequest = connect() // Connect is idempotent and does not connect on every call.
                .switchMap(new Func1<ServerConnection, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ServerConnection serverConnection) {
                        return serverConnection.send(new InterestRegistration(newInterest))
                                .doOnCompleted(new UpdateLocalInterest(newInterest));

                    }
                });

        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                if (STATES.Closed == state.get()) {
                    subscriber.onError(CHANNEL_CLOSED_EXCEPTION);
                }
                else if (state.compareAndSet(STATES.Idle, STATES.Open)) {
                    logger.debug("First time registration");
                    channelInterestStream.subscribe(channelInterestSubscriber);
                } else {
                    logger.debug("Channel changes");
                }
                subscriber.onCompleted();
            }
        }).concatWith(serverRequest);
    }

    @Override
    public Observable<Void> appendInterest(Interest<InstanceInfo> toAppend) {
        if (null == channelInterest) {
            return Observable.error(INTEREST_NOT_REGISTERED_EXCEPTION);
        }
        return change(channelInterest.copyAndAppend(toAppend));
    }

    @Override
    public Observable<Void> removeInterest(Interest<InstanceInfo> toRemove) {
        if (null == channelInterest) {
            return Observable.error(INTEREST_NOT_REGISTERED_EXCEPTION);
        }
        return change(channelInterest.copyAndRemove(toRemove));
    }

    @Override
    protected void _close() {
        state.set(STATES.Closed);
        idVsInstance.clear();
        super._close();
    }

    private Observable<ChangeNotification<InstanceInfo>> createInterestStream() {

        return connect().switchMap(new Func1<ServerConnection, Observable<? extends ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<? extends ChangeNotification<InstanceInfo>> call(final ServerConnection connection) {
                return connection.getInput().filter(new Func1<Object, Boolean>() {
                    @Override
                    public Boolean call(Object message) {
                        boolean isKnown = message instanceof InterestSetNotification;
                        if (!isKnown) {
                            logger.warn("Unrecognized discovery protocol message of type " + message.getClass());
                        }
                        return isKnown;
                    }
                }).map(new Func1<Object, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(Object message) {
                        InterestSetNotification notification = (InterestSetNotification) message;
                        if (notification instanceof AddInstance) {
                            InstanceInfo instanceInfo = ((AddInstance) notification).getInstanceInfo();
                            idVsInstance.put(instanceInfo.getId(), instanceInfo);
                            sendAckOnConnection(connection);
                            return new ChangeNotification<>(ChangeNotification.Kind.Add, instanceInfo);
                        } else if (notification instanceof UpdateInstanceInfo) {
                            Delta delta = ((UpdateInstanceInfo) notification).getDelta();
                            InstanceInfo oldInfo = idVsInstance.get(delta.getId());
                            if (oldInfo != null) {
                                InstanceInfo updatedInfo = oldInfo.applyDelta(delta);
                                idVsInstance.put(updatedInfo.getId(), updatedInfo);
                                sendAckOnConnection(connection);
                                @SuppressWarnings("unchecked")
                                ModifyNotification<InstanceInfo> modify =
                                        new ModifyNotification(updatedInfo, Collections.singleton(delta));
                                return modify;
                            }

                            sendAckOnConnection(connection); // Non-existent instance update isn't an error.

                            if (logger.isWarnEnabled()) {
                                logger.warn("Update notification received for non-existent instance id " + delta.getId());
                            }
                            return null;
                        } else if (notification instanceof DeleteInstance) {
                            String instanceId = ((DeleteInstance) notification).getInstanceId();
                            InstanceInfo removedInstance = idVsInstance.remove(instanceId);
                            sendAckOnConnection(connection);
                            if (removedInstance != null) {
                                return new ChangeNotification<>(ChangeNotification.Kind.Delete,
                                                                            removedInstance);
                            }
                            sendAckOnConnection(connection); // Non-existent instance delete isn't an error.
                            if (logger.isWarnEnabled()) {
                                logger.warn("Delete notification received for non-existent instance id:" + instanceId);
                            }
                            return null;
                        } else {
                            throw new IllegalArgumentException("Unknown message received on the interest channel. Type: "
                                                               + message.getClass().getName());
                        }
                    }
                }).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return null != notification;
                    }
                });
            }
        });
    }

    protected static class ChannelInterestSubscriber extends SafeSubscriber<ChangeNotification<InstanceInfo>> {
        public ChannelInterestSubscriber(final EurekaRegistry<InstanceInfo> registry) {
            super(new Subscriber<ChangeNotification<InstanceInfo>>() {
                @Override
                public void onCompleted() {
                    // TODO: handle
                    logger.debug("Channel interest completed");
                }

                @Override
                public void onError(Throwable e) {
                    // TODO: handle/do failover/fallback
                    logger.error("Channel interest throw error", e);
                }

                @Override
                public void onNext(ChangeNotification<InstanceInfo> notification) {
                    switch (notification.getKind()) {  // these are in-mem blocking ops
                        case Add:
                            registry.register(notification.getData());
                            break;
                        case Modify:
                            ModifyNotification<InstanceInfo> modifyNotification
                                    = (ModifyNotification<InstanceInfo>) notification;
                            registry.update(modifyNotification.getData(), modifyNotification.getDelta());
                            break;
                        case Delete:
                            registry.unregister(notification.getData().getId());
                            break;
                        default:
                            logger.error("Unrecognized notification kind");
                    }
                }
            });
        }
    }

    private class UpdateLocalInterest implements Action0 {
        private final Interest<InstanceInfo> interest;

        public UpdateLocalInterest(Interest<InstanceInfo> interest) {
            this.interest = interest;
        }

        @Override
        public void call() {
            channelInterest = new MultipleInterests<>(interest);
        }
    }
}
