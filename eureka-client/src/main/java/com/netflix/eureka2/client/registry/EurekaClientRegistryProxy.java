package com.netflix.eureka2.client.registry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientInterestChannel;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.service.InterestChannel;
import rx.Observable;
import rx.Subscriber;

/**
 * An implementation of {@link EurekaRegistry} to be used by the eureka client.
 *
 * This registry abstracts the {@link InterestChannel} interaction from the consumers of this registry and transparently
 * reconnects when a channel is broken.
 *
 * <h2>Storage</h2>
 *
 * This registry uses {@link EurekaClientRegistryImpl} for actual data storage.
 *
 * <h2>Reconnects</h2>
 *
 * Whenever the used {@link InterestChannel} is broken, this class holds the last known registry information till the
 * time it is successfully able to reconnect and relay the last know interest set to the new {@link InterestChannel}.
 * On a successful reconnect, the old registry data is disposed and the registry is created afresh from the instance
 * stream from the new {@link InterestChannel}
 *
 * @author Nitesh Kant
 */
@Singleton
public class EurekaClientRegistryProxy implements EurekaClientRegistry<InstanceInfo> {

    private final ClientChannelFactory channelFactory;
    private final EurekaClientRegistry<InstanceInfo> registry;
    private final ClientInterestChannel interestChannel;

    @Inject
    public EurekaClientRegistryProxy(ClientChannelFactory channelFactory, EurekaClientMetricFactory metricFactory) {
        this.channelFactory = channelFactory;
        this.registry = new EurekaClientRegistryImpl(metricFactory.getRegistryMetrics());
        this.interestChannel = channelFactory.newInterestChannel(registry);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return registry.register(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return registry.unregister(instanceInfo);
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        return registry.update(updatedInfo, deltas);
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        return registry.forSnapshot(interest);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        Observable toReturn = interestChannel
                .appendInterest(interest).cast(ChangeNotification.class)
                .mergeWith(registry.forInterest(interest));

        return toReturn;
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                interestChannel.close();
                channelFactory.shutdown();  // channelFactory will shutdown registry and transport clients
            }
        });
    }

    @Override
    public String toString() {
        return registry.toString();
    }
}
