package com.netflix.eureka2.server.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

import com.google.inject.Provider;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.service.selfinfo.CachingSelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.ChainableSelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.ConfigSelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.PeriodicDataCenterInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolverChain;
import com.netflix.eureka2.server.service.selfinfo.StatusInfoResolver;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.tcp.interest.TcpInterestServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author David Liu
 */
@Singleton
public class EurekaWriteServerSelfInfoResolver implements SelfInfoResolver {

    private final SelfInfoResolver delegate;

    @Inject
    public EurekaWriteServerSelfInfoResolver(
            final WriteServerConfig config,
            final EurekaHttpServer httpServer,
            final Provider<TcpRegistrationServer> registrationServer,
            final Provider<TcpReplicationServer> replicationServer,
            final Provider<TcpInterestServer> discoveryServer,
            final EurekaHealthStatusAggregatorImpl healthStatusAggregator) {

        SelfInfoResolverChain resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config.getEurekaInstance()),
                new StatusInfoResolver(healthStatusAggregator),
                // write server specific resolver
                new ChainableSelfInfoResolver(Observable.just(new HashSet<ServicePort>())
                        .map(new Func1<HashSet<ServicePort>, InstanceInfoBuilder>() {
                            @Override
                            public InstanceInfoBuilder call(HashSet<ServicePort> ports) {
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.EUREKA_HTTP, httpServer.serverPort(), false));
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.REGISTRATION, registrationServer.get().serverPort(), false));
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.REPLICATION, replicationServer.get().serverPort(), false));
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.INTEREST, discoveryServer.get().serverPort(), false));

                                return InstanceModel.getDefaultModel().newInstanceInfo().withPorts(ports);
                            }
                        })
                ),
                new ChainableSelfInfoResolver(Observable.just(InstanceModel.getDefaultModel().newInstanceInfo()
                        .withMetaData(META_EUREKA_SERVER_TYPE, ServerType.Write.name())
                        .withMetaData(META_EUREKA_WRITE_CLUSTER_ID, config.getEurekaInstance().getEurekaVipAddress())
                )),
                new PeriodicDataCenterInfoResolver(config.getEurekaInstance(), config.getEurekaTransport())
        );

        delegate = new CachingSelfInfoResolver(resolverChain);
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return delegate.resolve();
    }
}