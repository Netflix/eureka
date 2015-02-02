package com.netflix.eureka2.client.interest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.client.channel.ActiveInterestsTracker;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientInterestChannel;
import com.netflix.eureka2.client.channel.InterestChannelImpl;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.StreamState;
import com.netflix.eureka2.interests.StreamState.Kind;
import com.netflix.eureka2.interests.SourcedChangeNotification;
import com.netflix.eureka2.interests.SourcedModifyNotification;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;

/**
 * @author David Liu
 */
@Singleton
public class InterestHandlerImpl implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(InterestChannelImpl.class);

    private final AtomicBoolean isShutdown;
    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final ClientInterestChannel interestChannel;

    @Inject
    public InterestHandlerImpl(SourcedEurekaRegistry<InstanceInfo> registry,
                               ClientChannelFactory<ClientInterestChannel> channelFactory) {
        this.registry = registry;
        this.interestChannel = channelFactory.newChannel();
        this.isShutdown = new AtomicBoolean(false);
    }

    /**
     * TODO: is the conversion necessary? This would affect equals which might be necessary
     * Get interest stream from the registry, and additionally convert from sourced notifications to base notifications
     * if necessary. We don't want to expose "sourced" types to client users.
     */
    private Observable<ChangeNotification<InstanceInfo>> forInterestFromRegistry(final Interest<InstanceInfo> interest) {
        return registry.forInterest(interest)
                .map(new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification instanceof SourcedChangeNotification) {
                            return ((SourcedChangeNotification<InstanceInfo>) notification).toBaseNotification();
                        } else if (notification instanceof SourcedModifyNotification) {
                            return ((SourcedModifyNotification<InstanceInfo>) notification).toBaseNotification();
                        }
                        return notification;
                    }
                });
    }


    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        final Observable reply = interestChannel.appendInterest(interest)
                .cast(ChangeNotification.class)
                .mergeWith(forInterestFromRegistry(interest))
                .flatMap(new SnapshotTrackingFun(interestChannel, interest))
                .onErrorResumeNext(new Func1<Throwable, Observable<ChangeNotification>>() {
                    @Override
                    public Observable<ChangeNotification> call(Throwable throwable) {
                        if (isShutdown.get()) {
                            return Observable.empty();
                        } else {
                            return Observable.error(throwable);
                        }
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        interestChannel.removeInterest(interest).subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.warn("forInterest unsubscribe failed", e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        });
                    }
                });

        return reply;
    }

    @Override
    public void shutdown() {
        isShutdown.set(true);
        interestChannel.close();
        registry.shutdown();
    }

    /**
     * From a client perspective the snapshot stream is regarded as completed under two conditions:
     * <ul>
     *     <li>- snapshot from the registry is sent to the client (cache)</li>
     *     <li>- snapshots from the active channel, corresponding to the client's interest set are completed</li>
     * </ul>
     *
     * As the client subscription is not handled by the same event loop as registry updated from the stream, there
     * is a possibility that snapshot end notification is inserted before all items from the registry are consumed.
     * This seems to be a valid compromise, to avoid excessive synchronization/state tracking to get exact
     * delineation.
     */
    static class SnapshotTrackingFun implements Func1<ChangeNotification, Observable<ChangeNotification>> {

        enum State {RegistrySnapshot, ChannelSnapshot, Live}

        private final ClientInterestChannel interestChannel;
        private final Interest<InstanceInfo> interest;
        private final ActiveInterestsTracker registryInterestTracker = new ActiveInterestsTracker();
        private State state = State.RegistrySnapshot;

        SnapshotTrackingFun(ClientInterestChannel interestChannel, Interest<InstanceInfo> interest) {
            this.interestChannel = interestChannel;
            this.interest = interest;
            registryInterestTracker.appendInterest(interest);
        }

        @Override
        public Observable<ChangeNotification> call(ChangeNotification changeNotification) {
            boolean isStatusUpdate = changeNotification instanceof StreamStateNotification;
            if (state == State.Live) {
                if (isStatusUpdate) {
                    logger.warn("Unexpected snapshot marker in notification stream");
                    return Observable.empty();
                }
                return Observable.just(changeNotification);
            }
            if (isStatusUpdate) {
                registryInterestTracker.updateState((StreamStateNotification<InstanceInfo>) changeNotification);
                if (registryInterestTracker.isSnapshotCompleted()) {
                    state = State.ChannelSnapshot;
                }
            }
            if (state == State.ChannelSnapshot) {
                if (interestChannel.subscriptionStatusInChannel().isSnapshotCompleted(interest)) {
                    state = State.Live;
                    ChangeNotification subscriberStatusUpdate = new StreamStateNotification(new StreamState(Kind.Live, interest));
                    if (isStatusUpdate) {
                        return Observable.just(subscriberStatusUpdate);
                    }
                    return Observable.just(subscriberStatusUpdate, changeNotification);
                }
            }
            if (isStatusUpdate) {
                return Observable.empty();
            } else {
                return Observable.just(changeNotification);
            }
        }
    }
}
