package com.netflix.eureka2.client.interest;

import javax.inject.Inject;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.EmptyRegistryInterest;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func2;

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

    private final InterestTracker interestTracker;
    private final RetryableConnection<InterestChannel> retryableConnection;

    @Inject
    public EurekaInterestClientImpl(SourcedEurekaRegistry<InstanceInfo> registry,
                                    ChannelFactory<InterestChannel> channelFactory) {
        this(registry, channelFactory, DEFAULT_RETRY_WAIT_MILLIS);
    }

    /* visible for testing*/ EurekaInterestClientImpl(final SourcedEurekaRegistry<InstanceInfo> registry,
                                                      ChannelFactory<InterestChannel> channelFactory,
                                                      int retryWaitMillis) {
        super(registry, retryWaitMillis);
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

        registryEvictionSubscribe(retryableConnection);
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

        Observable toReturn = appendInterest
                .cast(ChangeNotification.class)
                .mergeWith(registry.forInterest(interest))
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
