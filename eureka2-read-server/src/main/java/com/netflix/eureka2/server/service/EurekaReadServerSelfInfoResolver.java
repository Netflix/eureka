package com.netflix.eureka2.server.service;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.service.selfinfo.*;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.EurekaTransportServer;
import rx.Observable;
import rx.functions.Func1;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

/**
 * @author David Liu
 */
@Singleton
public class EurekaReadServerSelfInfoResolver implements SelfInfoResolver {

    private final SelfInfoResolver delegate;

    @Inject
    public EurekaReadServerSelfInfoResolver(final EurekaServerConfig config,
                                            final EurekaHttpServer httpServer,
                                            final EurekaTransportServer discoveryServer,
                                            EurekaHealthStatusAggregatorImpl healthStatusAggregator) {

        SelfInfoResolverChain resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config.getEurekaInstance()),
                new StatusInfoResolver(healthStatusAggregator),
                // read server specific resolver
                new ChainableSelfInfoResolver(Observable.just(new HashSet<ServicePort>())
                        .map(new Func1<HashSet<ServicePort>, InstanceInfoBuilder>() {
                            @Override
                            public InstanceInfoBuilder call(HashSet<ServicePort> ports) {
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.EUREKA_HTTP, httpServer.serverPort(), false));
                                ports.add(InstanceModel.getDefaultModel().newServicePort(Names.EUREKA_SERVICE, discoveryServer.getServerPort(), false));
                                return InstanceModel.getDefaultModel().newInstanceInfo().withPorts(ports);
                            }
                        })
                ),
                new ChainableSelfInfoResolver(Observable.just(InstanceModel.getDefaultModel().newInstanceInfo()
                        .withMetaData(META_EUREKA_SERVER_TYPE, ServerType.Read.name())
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
