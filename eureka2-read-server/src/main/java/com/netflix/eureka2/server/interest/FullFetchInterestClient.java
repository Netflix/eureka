package com.netflix.eureka2.server.interest;

import javax.inject.Inject;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.interest.AbstractInterestClient;
import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.functions.Func1;

/**
 * {@link EurekaInterestClient} implementation with single full registry fetch subscription.
 * Client interest subscriptions are not propagated to the channel, as the registry is already eagerly
 * subscribed to the full content.
 *
 * @author Tomasz Bak
 */
public class FullFetchInterestClient extends AbstractInterestClient {

    private final RetryableConnection<InterestChannel> retryableConnection;

    @Inject
    public FullFetchInterestClient(SourcedEurekaRegistry<InstanceInfo> registry,
                                   ChannelFactory<InterestChannel> channelFactory) {
        this(registry, channelFactory, DEFAULT_RETRY_WAIT_MILLIS);
    }

    /* visible for testing*/ FullFetchInterestClient(final SourcedEurekaRegistry<InstanceInfo> registry,
                                                     ChannelFactory<InterestChannel> channelFactory,
                                                     int retryWaitMillis) {
        super(registry, retryWaitMillis);


        RetryableConnectionFactory<InterestChannel> retryableConnectionFactory
                = new RetryableConnectionFactory<>(channelFactory);

        Func1<InterestChannel, Observable<Void>> executeOnChannel = new Func1<InterestChannel, Observable<Void>>() {
            @Override
            public Observable<Void> call(InterestChannel interestChannel) {
                return interestChannel.change(Interests.forFullRegistry());
            }
        };

        this.retryableConnection = retryableConnectionFactory.zeroOpConnection(executeOnChannel);

        registryEvictionSubscribe(retryableConnection);
        lifecycleSubscribe(retryableConnection);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        if (isShutdown.get()) {
            return Observable.error(new IllegalStateException("InterestHandler has shutdown"));
        }
        return registry.forInterest(interest);
    }

    @Override
    protected RetryableConnection<InterestChannel> getRetryableConnection() {
        return retryableConnection;
    }
}
