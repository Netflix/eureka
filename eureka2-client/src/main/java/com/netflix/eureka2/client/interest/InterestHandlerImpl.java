package com.netflix.eureka2.client.interest;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.SourcedChangeNotification;
import com.netflix.eureka2.interests.SourcedModifyNotification;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;
import rx.functions.Func2;

/**
 * Each InterestHandler class contains an interest channel and handles the lifecycle and reconnect of this channel.
 * At any point in time, this handler will have a single interest channel active, however this channel may not be
 * the same channel over time as it is refreshed. A retryableConnection is used for this purpose.
 *
 * When a client forInterest is invoked, this appends the interest to the stateful interestTracker.
 * The retryableChannel observable eagerly subscribes to the interest stream from the interestTracker and upgrades
 * the underlying interest channel when necessary.
 *
 * @author David Liu
 */
public class InterestHandlerImpl implements InterestHandler {
    private static final Logger logger = LoggerFactory.getLogger(InterestHandlerImpl.class);

    private static final int DEFAULT_RETRY_WAIT_MILLIS = 500;

    private final AtomicBoolean isShutdown;
    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final InterestTracker interestTracker;

    private final RetryableConnection<InterestChannel, ChangeNotification<InstanceInfo>> retryableConnection;
    private final Observable<Boolean> channelStateChangeObservable;
    private final BatchingTracker channelBatchingTracker;

    @Inject
    public InterestHandlerImpl(SourcedEurekaRegistry<InstanceInfo> registry,
                               ChannelFactory<InterestChannel> channelFactory) {
        this(registry, channelFactory, DEFAULT_RETRY_WAIT_MILLIS);
    }

    /* visible for testing*/ InterestHandlerImpl(final SourcedEurekaRegistry<InstanceInfo> registry,
                                                 ChannelFactory<InterestChannel> channelFactory,
                                                 int retryWaitMillis) {
        this.registry = registry;
        this.interestTracker = new InterestTracker();
        this.isShutdown = new AtomicBoolean(false);


        RetryableConnectionFactory<InterestChannel> retryableConnectionFactory
                = new RetryableConnectionFactory<>(channelFactory);

        Observable<Interest<InstanceInfo>> opStream = interestTracker.interestChangeStream();
        Func2<InterestChannel, Interest<InstanceInfo>, Observable<Void>> executeOnChannel = new Func2<InterestChannel, Interest<InstanceInfo>, Observable<Void>>() {
            @Override
            public Observable<Void> call(InterestChannel interestChannel, Interest<InstanceInfo> interest) {
                return interestChannel.change(interest);
            }
        };

        Func1<InterestChannel, Observable<ChangeNotification<InstanceInfo>>> connectToInputChannel = new Func1<InterestChannel, Observable<ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<ChangeNotification<InstanceInfo>> call(InterestChannel interestChannel) {
                return interestChannel.changeNotifications();
            }
        };
        this.retryableConnection = retryableConnectionFactory.singleOpConnection(opStream, executeOnChannel, connectToInputChannel);

        // subscribe to the base interest channels to do cleanup on every channel refresh.
        retryableConnection.getChannelObservable()
                .flatMap(new Func1<InterestChannel, Observable<Long>>() {
                    @Override
                    public Observable<Long> call(InterestChannel interestChannel) {
                        if (interestChannel instanceof Sourced) {
                            Source toRetain = ((Sourced) interestChannel).getSource();
                            return registry.evictAllExcept(toRetain);
                        }
                       return Observable.empty();
                    }
                })
                .subscribe(new Subscriber<Long>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Completed one round of eviction due to a new interestChannel creation");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.warn("OnError in one round of eviction due to a new interestChannel creation");
                    }

                    @Override
                    public void onNext(Long aLong) {
                        logger.info("Evicted {} instances in one round of eviction due to a new interestChannel creation", aLong);
                    }
                });

        // subscribe to the lifecycle to initiate the interest subscription
        retryableConnection.getRetryableLifecycle()
                .retryWhen(new RetryStrategyFunc(retryWaitMillis))
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("channel onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Lifecycle closed with an error");
                    }

                    @Override
                    public void onNext(Void aVoid) {

                    }
                });

        this.channelBatchingTracker = new BatchingTracker();
        channelBatchingTracker.subscribeForCleanup(interestTracker.interestChangeStream());

        this.channelStateChangeObservable = retryableConnection.getChannelInputObservable()
                .map(channelBatchingTracker.markerUpdateFun()).share();
        channelStateChangeObservable.subscribe(new NoOpSubscriber<>());
    }

    /**
     * TODO: make a final decision on:
     * if channelLifecycle retries enough times and does not succeed, do we indefinitely retry and/or propagate
     * the error to all the subscribers of forInterests? We could also do this through the registry index.
     */
    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        if (isShutdown.get()) {
            return Observable.error(new IllegalStateException("InterestHandler has shutdown"));
        }

        Observable<Void> appendInterest = Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                interestTracker.appendInterest(interest);
                subscriber.onCompleted();
            }
        });

        Observable toReturn = appendInterest
                .cast(ChangeNotification.class)
                .mergeWith(forInterestFromRegistry(interest))
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        interestTracker.removeInterest(interest);
                    }
                });

        return toReturn;
    }

    /**
     * Get interest stream from the registry, and additionally convert from sourced notifications to base notifications
     * if necessary. We don't want to expose "sourced" types to client users, hence this convertion
     */
    private Observable<ChangeNotification<InstanceInfo>> forInterestFromRegistry(final Interest<InstanceInfo> interest) {
        return Observable.create(new OnSubscribe<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<InstanceInfo>> subscriber) {
                ConnectableObservable<ChangeNotification<InstanceInfo>> notifications = registry.forInterest(interest).publish();

                BatchingTracker registryBatchingTracker = new BatchingTracker();
                Observable<Boolean> registryStateChangeObservable = notifications.map(registryBatchingTracker.markerUpdateFun());

                // Batching is defined by merge of batch hint from the registry and the channel
                // As we connect to the hot channel stream stream, we send one item first to enforce channel batching state
                // evaluation for this subscribing client.
                Observable<ChangeNotification<InstanceInfo>> batchingNotifications = BatchingTracker.combine(
                        Observable.just(Boolean.TRUE).concatWith(channelStateChangeObservable).map(channelBatchingTracker.batchingFun(interest)),
                        registryStateChangeObservable.map(registryBatchingTracker.batchingFun(interest))
                );

                // Plain data change notification stream
                Observable<ChangeNotification<InstanceInfo>> dataNotifications = notifications
                        .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                            @Override
                            public Boolean call(ChangeNotification<InstanceInfo> notification) {
                                return notification.isDataNotification();
                            }
                        }).map(new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                            @Override
                            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                                if (notification instanceof SourcedChangeNotification) {
                                    return ((SourcedChangeNotification<InstanceInfo>) notification).toBaseNotification();
                                }
                                if (notification instanceof SourcedModifyNotification) {
                                    return ((SourcedModifyNotification<InstanceInfo>) notification).toBaseNotification();
                                }
                                return notification;
                            }
                        });

                PublishSubject<ChangeNotification<InstanceInfo>> resultSubject = PublishSubject.create();
                resultSubject.subscribe(subscriber);

                batchingNotifications.subscribe(resultSubject);
                dataNotifications.subscribe(resultSubject);
                notifications.connect();
            }
        });
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.info("Shutting down InterestHandler");
            retryableConnection.close();
            registry.shutdown();
        }
    }
}
