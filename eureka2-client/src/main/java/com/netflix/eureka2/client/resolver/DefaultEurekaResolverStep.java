package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistryImpl;
import com.netflix.eureka2.client.interest.EurekaInterestClientImpl;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.utils.rx.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This resolver uses a custom, light weight eureka interest client for reading eureka read server data from
 * the remote server.
 *
 * @author David Liu
 */
class DefaultEurekaResolverStep implements EurekaRemoteResolverStep {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEurekaResolverStep.class);

    private final ServiceSelector serviceSelector = ServiceSelector.selectBy()
            .serviceLabel(Names.DISCOVERY).protocolType(NetworkAddress.ProtocolType.IPv4).publicIp(true)
            .or()
            .serviceLabel(Names.DISCOVERY).protocolType(NetworkAddress.ProtocolType.IPv4);

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
                instanceInfoSource.map(Eureka.interestFunctions().instanceInfoToServer(serviceSelector))
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

            BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new BatchingRegistryImpl<>();
            SourcedEurekaRegistry<InstanceInfo> registry = new PassThroughRegistry(remoteBatchingRegistry);

            ClientChannelFactory<InterestChannel> channelFactory
                    = new InterestChannelFactory(transportConfig, serverResolver, registry, remoteBatchingRegistry, clientMetricFactory);

            return new EurekaInterestClientImpl(registry, channelFactory);
        }
    }


    static class PassThroughRegistry implements SourcedEurekaRegistry<InstanceInfo> {

        private final BatchingRegistry<InstanceInfo> remoteBatchingRegistry;
        private final Subject<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>> relay = PublishSubject.create();

        PassThroughRegistry(BatchingRegistry<InstanceInfo> remoteBatchingRegistry) {
            this.remoteBatchingRegistry = remoteBatchingRegistry;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Observable<Boolean> register(InstanceInfo instanceInfo, Source source) {
            relay.onNext(new ChangeNotification<>(ChangeNotification.Kind.Add ,instanceInfo));
            return Observable.just(true);
        }

        @Override
        public Observable<Boolean> unregister(InstanceInfo instanceInfo, Source source) {
            relay.onNext(new ChangeNotification<>(ChangeNotification.Kind.Delete ,instanceInfo));
            return Observable.just(true);
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
            return relay.mergeWith(
                    remoteBatchingRegistry.forInterest(interest)
                            .map(new Func1<StreamStateNotification.BufferState, ChangeNotification<InstanceInfo>>() {
                                @Override
                                public ChangeNotification<InstanceInfo> call(StreamStateNotification.BufferState state) {
                                    if (state == StreamStateNotification.BufferState.BufferEnd) {
                                        return new StreamStateNotification<>(state, interest);
                                    }
                                    return null;
                                }
                            })
                            .filter(RxFunctions.filterNullValuesFunc())
            );
        }

        @Override
        public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
            return Observable.error(new UnsupportedOperationException("Not supported"));
        }

        @Override
        public Observable<Long> evictAllExcept(Source.SourceMatcher retainMatcher) {
            return Observable.just(0l);
        }

        @Override
        public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
            return Observable.error(new UnsupportedOperationException("Not supported"));
        }

        @Override
        public Observable<Void> shutdown() {
            remoteBatchingRegistry.shutdown();
            relay.onCompleted();
            return Observable.empty();
        }

        @Override
        public Observable<Void> shutdown(Throwable cause) {
            remoteBatchingRegistry.shutdown();
            relay.onError(cause);
            return Observable.empty();
        }
    }
}
