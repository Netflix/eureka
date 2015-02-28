package com.netflix.eureka2.server.interest;

import javax.inject.Inject;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.interest.AbstractInterestClient;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.health.AbstractHealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.health.SubsystemDescriptor;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

/**
 * {@link EurekaInterestClient} implementation with single full registry fetch subscription.
 * Client interest subscriptions are not propagated to the channel, as the registry is already eagerly
 * subscribed to the full content.
 *
 * @author Tomasz Bak
 */
public class FullFetchInterestClient extends AbstractInterestClient implements HealthStatusProvider<FullFetchInterestClient> {

    private static final SubsystemDescriptor<FullFetchInterestClient> DESCRIPTOR = new SubsystemDescriptor<>(
            FullFetchInterestClient.class,
            "Read Server full fetch InterestClient",
            "Source of registry data for Eureka read server clients."
    );

    private final RetryableConnection<InterestChannel> retryableConnection;
    private final FullFetchInterestClientHealth healthProvider;

    @Inject
    public FullFetchInterestClient(SourcedEurekaRegistry<InstanceInfo> registry,
                                   ChannelFactory<InterestChannel> channelFactory) {
        this(registry, channelFactory, DEFAULT_RETRY_WAIT_MILLIS);
    }

    /* visible for testing*/ FullFetchInterestClient(final SourcedEurekaRegistry<InstanceInfo> registry,
                                                     ChannelFactory<InterestChannel> channelFactory,
                                                     int retryWaitMillis) {
        super(registry, retryWaitMillis);

        this.healthProvider = new FullFetchInterestClientHealth();

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
        bootstrapUploadSubscribe();
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

    @Override
    public void shutdown() {
        healthProvider.moveHealthTo(Status.DOWN);
        super.shutdown();
    }

    @Override
    public Observable<HealthStatusUpdate<FullFetchInterestClient>> healthStatus() {
        return healthProvider.healthStatus();
    }

    /**
     * Eureka Read server registry is ready when the initial batch of data is uploaded from the server.
     */
    private void bootstrapUploadSubscribe() {
        forInterest(Interests.forFullRegistry()).takeWhile(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> notification) {
                return notification.getKind() != Kind.BufferSentinel;
            }
        }).doOnCompleted(new Action0() {
            @Override
            public void call() {
                healthProvider.moveHealthTo(Status.UP);
            }
        }).subscribe();
    }

    public static class FullFetchInterestClientHealth extends AbstractHealthStatusProvider<FullFetchInterestClient> {

        protected FullFetchInterestClientHealth() {
            super(Status.STARTING, DESCRIPTOR);
        }

        @Override
        public Status toEurekaStatus(Status status) {
            return status;
        }
    }
}
