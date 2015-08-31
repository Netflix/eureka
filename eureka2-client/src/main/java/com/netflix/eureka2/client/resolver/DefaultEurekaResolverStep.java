package com.netflix.eureka2.client.resolver;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.client.interest.EurekaInterestClientImpl;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.data.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * This resolver uses a custom, light weight eureka interest client for reading eureka read server data from
 * the remote server.
 *
 * @author David Liu
 */
class DefaultEurekaResolverStep implements EurekaRemoteResolverStep {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEurekaResolverStep.class);

    public static final String RESOLVER_CLIENT_ID = "resolverClient";

    private final ServiceSelector serviceSelector = ServiceSelector.selectBy()
            .serviceLabel(Names.INTEREST).protocolType(NetworkAddress.ProtocolType.IPv4).publicIp(true)
            .or()
            .serviceLabel(Names.INTEREST).protocolType(NetworkAddress.ProtocolType.IPv4);

    private final EurekaInterestClientBuilder interestClientBuilder;

    DefaultEurekaResolverStep(ServerResolver bootstrapResolver) {
        this(new ResolverEurekaInterestClientBuilder().withServerResolver(bootstrapResolver));
    }

    /* for testing */ DefaultEurekaResolverStep(EurekaInterestClientBuilder interestClientBuilder) {
        this.interestClientBuilder = interestClientBuilder;
    }

    @Override
    public ServerResolver forInterest(final Interest<InstanceInfo> interest) {
        final AtomicReference<EurekaInterestClient> interestClientRef = new AtomicReference<>();
        final AtomicLong duration = new AtomicLong();
        Observable<ChangeNotification<InstanceInfo>> instanceInfoSource = Observable
                .create(new Observable.OnSubscribe<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(Subscriber<? super ChangeNotification<InstanceInfo>> subscriber) {
                        logger.info("Starting lite interestClient for eureka resolver");
                        EurekaInterestClient interestClient = interestClientBuilder.build();
                        interestClientRef.set(interestClient);
                        duration.set(System.currentTimeMillis());
                        interestClient.forInterest(interest).subscribe(subscriber);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        EurekaInterestClient interestClient = interestClientRef.getAndSet(null);
                        if (interestClient != null) {
                            interestClient.shutdown();
                            logger.info("Shutting down lite interestClient for eureka resolver");
                        }
                        logger.info("Populating from remote eureka server took {} ms", (System.currentTimeMillis() - duration.get()));
                    }
                });

        ServerResolver resolver = ServerResolvers.fromServerSource(
                instanceInfoSource.map(InterestFunctions.instanceInfoToServer(serviceSelector))
        );

        return resolver;
    }


    @SuppressWarnings("deprecation")
    static class ResolverEurekaInterestClientBuilder extends EurekaInterestClientBuilder {

        @Override
        protected EurekaInterestClient buildClient() {
            if (serverResolver == null) {
                throw new IllegalArgumentException("Cannot build client for discovery without read server resolver");
            }

            EurekaRegistry<InstanceInfo> registry = new PassThroughRegistry();

            ClientChannelFactory<InterestChannel> channelFactory
                    = new InterestChannelFactory(RESOLVER_CLIENT_ID, transportConfig, serverResolver, registry, clientMetricFactory);

            return new EurekaInterestClientImpl(registry, channelFactory);
        }
    }


    static class PassThroughRegistry implements EurekaRegistry<InstanceInfo> {

        private final Subject<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>> relay = PublishSubject.create();

        @Override
        public int size() {
            return 0;
        }


        @Override
        public Observable<Void> connect(Source source, Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
            throw new IllegalStateException("method not supported");
        }

        @Override
        public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
            return Observable.error(new UnsupportedOperationException("Not supported"));
        }

        @Override
        public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
            return Observable.error(new UnsupportedOperationException("Not supported"));
        }

        @Override
        public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
            return relay;
        }

        @Override
        public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
            return Observable.error(new UnsupportedOperationException("Not supported"));
        }

        @Override
        public Observable<Long> evictAll(Source.SourceMatcher evictionMatcher) {
            return Observable.just(0l);
        }

        @Override
        public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
            return Observable.error(new UnsupportedOperationException("Not supported"));
        }

        @Override
        public Observable<Void> shutdown() {
            relay.onCompleted();
            return Observable.empty();
        }

        @Override
        public Observable<Void> shutdown(Throwable cause) {
            relay.onError(cause);
            return Observable.empty();
        }

        @Override
        public Source getSource() {
            return null;
        }
    }
}
