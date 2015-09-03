package com.netflix.eureka2.client.interest;

import javax.inject.Inject;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.EmptyRegistryInterest;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.utils.functions.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

/**
 * Each InterestClient class contains an interest channel and handles the lifecycle and reconnect of this channel.
 * At any point in time, this handler will have a single interest channel active, however this channel may not be
 * the same channel over time as it is refreshed. A retryableConnection is used for this purpose.
 *
 * When a client forInterest is invoked, this appends the interest to the stateful interestTracker.
 * The retryableChannel observable eagerly subscribes to the interest stream from the interestTracker and upgrades
 * the underlying interest channel when necessary.
 *
 * @author David Liu
 */
public class EurekaInterestClientImpl extends AbstractInterestClient {
    private static final Logger logger = LoggerFactory.getLogger(EurekaInterestClientImpl.class);

    private final InterestTracker interestTracker;
    private final RetryableConnection<InterestChannel> retryableConnection;

    @Inject
    public EurekaInterestClientImpl(EurekaRegistry<InstanceInfo> registry,
                                    ChannelFactory<InterestChannel> channelFactory) {
        this(registry, channelFactory, DEFAULT_RETRY_WAIT_MILLIS, Schedulers.computation());
    }

    /* visible for testing*/ EurekaInterestClientImpl(final EurekaRegistry<InstanceInfo> registry,
                                                      ChannelFactory<InterestChannel> channelFactory,
                                                      int retryWaitMillis,
                                                      Scheduler scheduler) {
        super(registry, retryWaitMillis, scheduler);
        this.interestTracker = new InterestTracker();

        RetryableConnectionFactory<InterestChannel> retryableConnectionFactory
                = new RetryableConnectionFactory<>(channelFactory);

        Observable<Interest<InstanceInfo>> opStream = interestTracker.interestChangeStream();
        Func2<InterestChannel, Interest<InstanceInfo>, Observable<Void>> executeOnChannel = new Func2<InterestChannel, Interest<InstanceInfo>, Observable<Void>>() {
            @Override
            public Observable<Void> call(InterestChannel interestChannel, Interest<InstanceInfo> interest) {
                return interestChannel.change(interest);
            }
        };

        this.retryableConnection = retryableConnectionFactory.singleOpConnection(opStream, executeOnChannel);

        lifecycleSubscribe(retryableConnection);
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

        if (interest instanceof EmptyRegistryInterest) {
            return Observable.empty();
        }
        if (interest instanceof MultipleInterests) {
            MultipleInterests<InstanceInfo> multiple = (MultipleInterests<InstanceInfo>) interest;
            if (multiple.flatten().isEmpty()) {
                return Observable.empty();
            }
        }

        Observable<Void> appendInterest = Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                interestTracker.appendInterest(interest);
                subscriber.onCompleted();
            }
        });

        // strip source from the notifications
        // convert bufferstart/bufferends to just a single buffersentinel
        Observable<ChangeNotification<InstanceInfo>> registrySteam = registry.forInterest(interest)
                .map(new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification instanceof SourcedChangeNotification) {
                            return ((SourcedChangeNotification) notification).toBaseNotification();
                        } else if (notification instanceof StreamStateNotification) {
                            StreamStateNotification<InstanceInfo> n = (StreamStateNotification) notification;
                            switch (n.getBufferState()) {
                                case BufferEnd:
                                    return ChangeNotification.bufferSentinel();
                                case BufferStart:
                                default:
                                    return null;
                            }
                        } else {
                            return notification;
                        }
                    }
                })
                .filter(RxFunctions.filterNullValuesFunc());

        Observable toReturn = appendInterest
                .cast(ChangeNotification.class)
                .mergeWith(registrySteam)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        interestTracker.removeInterest(interest);
                    }
                });

        return toReturn;
    }

    @Override
    protected RetryableConnection<InterestChannel> getRetryableConnection() {
        return retryableConnection;
    }
}
