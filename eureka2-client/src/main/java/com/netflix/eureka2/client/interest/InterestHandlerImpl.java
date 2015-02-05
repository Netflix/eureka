package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.SourcedChangeNotification;
import com.netflix.eureka2.interests.SourcedModifyNotification;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final RetryableConnection<InterestChannel> retryableConnection;

    @Inject
    public InterestHandlerImpl(SourcedEurekaRegistry<InstanceInfo> registry,
                               ChannelFactory<InterestChannel> channelFactory) {
        this(registry, channelFactory, DEFAULT_RETRY_WAIT_MILLIS);
    }

    /* visible for testing*/ InterestHandlerImpl(SourcedEurekaRegistry<InstanceInfo> registry,
                                                 ChannelFactory<InterestChannel> channelFactory,
                                                 int retryWaitMillis) {
        this.registry = registry;
        this.interestTracker = new InterestTracker();
        this.isShutdown = new AtomicBoolean(false);


        RetryableConnectionFactory<InterestChannel> retryableConnectionFactory
                = new RetryableConnectionFactory<>(channelFactory);

        this.retryableConnection = retryableConnectionFactory.unaryConnection(
                interestTracker.interestChangeStream(),
                new Func2<InterestChannel, Interest<InstanceInfo>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(InterestChannel interestChannel, Interest<InstanceInfo> interest) {
                        return interestChannel.change(interest);
                    }
                });

        retryableConnection.getRetryableLifecycle()
                .retryWhen(new RetryStrategyFunc(retryWaitMillis))
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("channel onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(Void aVoid) {

                    }
                });
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
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            logger.info("Shutting down InterestHandler");
            retryableConnection.close();
            registry.shutdown();
        }
    }
}
