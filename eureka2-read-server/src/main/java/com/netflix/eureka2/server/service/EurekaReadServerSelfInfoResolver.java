package com.netflix.eureka2.server.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author David Liu
 */
@Singleton
public class EurekaReadServerSelfInfoResolver implements SelfInfoResolver {

    private final SelfInfoResolver delegate;

    @Inject
    public EurekaReadServerSelfInfoResolver(final EurekaServerConfig config,
                                            final EurekaHttpServer httpServer,
                                            final TcpDiscoveryServer discoveryServer,
                                            EurekaHealthStatusAggregatorImpl healthStatusAggregator) {

        SelfInfoResolverChain resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config),
                new StatusInfoResolver(healthStatusAggregator),
                // read server specific resolver
                new ChainableSelfInfoResolver(Observable.just(new HashSet<ServicePort>())
                        .map(new Func1<HashSet<ServicePort>, InstanceInfo.Builder>() {
                            @Override
                            public InstanceInfo.Builder call(HashSet<ServicePort> ports) {
                                ports.add(new ServicePort(Names.EUREKA_HTTP, httpServer.serverPort(), false));
                                ports.add(new ServicePort(Names.DISCOVERY, discoveryServer.serverPort(), false));
                                return new InstanceInfo.Builder().withPorts(ports);
                            }
                        })
                ),
                new PeriodicDataCenterInfoResolver(config)
        );

        delegate = new CachingSelfInfoResolver(resolverChain);
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return delegate.resolve();
    }
}
