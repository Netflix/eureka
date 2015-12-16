package com.netflix.eureka2.server.service;

import com.google.inject.Provider;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.service.selfinfo.*;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.WriteTransportServer;
import rx.Observable;
import rx.functions.Func1;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

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
            final Provider<WriteTransportServer> registrationServer,
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
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.REGISTRATION, registrationServer.get().getServerPort(), false));
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.REPLICATION, registrationServer.get().getServerPort(), false));
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.INTEREST, registrationServer.get().getServerPort(), false));

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