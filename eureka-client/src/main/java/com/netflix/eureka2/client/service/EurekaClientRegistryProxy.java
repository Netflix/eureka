package com.netflix.eureka2.client.service;

import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.client.registry.EurekaClientRegistryImpl;
import com.netflix.eureka2.client.transport.TransportClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.service.EurekaService;
import com.netflix.eureka2.service.InterestChannel;
import rx.Observable;
import rx.Subscriber;

import javax.inject.Inject;
import java.util.Set;

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
public class EurekaClientRegistryProxy implements EurekaClientRegistry<InstanceInfo> {

    private final EurekaService service;
    private final EurekaClientRegistry<InstanceInfo> registry;
    private final ClientInterestChannel interestChannel;

    @Inject
    public EurekaClientRegistryProxy(final TransportClient readServerClient, EurekaClientMetricFactory metricFactory) {
        registry = new EurekaClientRegistryImpl(metricFactory.getRegistryMetrics());

        service = EurekaServiceImpl.forReadServer(registry, readServerClient, metricFactory);
        interestChannel = (ClientInterestChannel) service.newInterestChannel();
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
                service.shutdown();  // service will shutdown registry and transport clients
            }
        });
    }

    @Override
    public String toString() {
        return registry.toString();
    }
}
