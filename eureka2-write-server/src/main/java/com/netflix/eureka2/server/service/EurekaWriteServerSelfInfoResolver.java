package com.netflix.eureka2.server.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
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
            final EurekaServerConfig config,
            final EurekaHttpServer httpServer,
            final TcpRegistrationServer registrationServer,
            final TcpReplicationServer replicationServer,
            final TcpDiscoveryServer discoveryServer) {
        SelfInfoResolverChain resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config),
                // write server specific resolver
                new ChainableSelfInfoResolver(Observable.just(new HashSet<ServicePort>())
                        .map(new Func1<HashSet<ServicePort>, InstanceInfo.Builder>() {
                            @Override
                            public InstanceInfo.Builder call(HashSet<ServicePort> ports) {
                                ports.add(new ServicePort(Names.EUREKA_HTTP, httpServer.serverPort(), false));
                                ports.add(new ServicePort(Names.REGISTRATION, registrationServer.serverPort(), false));
                                ports.add(new ServicePort(Names.REPLICATION, replicationServer.serverPort(), false));
                                ports.add(new ServicePort(Names.DISCOVERY, discoveryServer.serverPort(), false));

                                return new InstanceInfo.Builder().withPorts(ports);
                            }
                        })
                ),
                new PeriodicDataCenterInfoResolver(config),
                // TODO override with more meaningful health check
                new ChainableSelfInfoResolver(Observable.just(new InstanceInfo.Builder().withStatus(InstanceInfo.Status.UP)))
        );

        delegate = new CachingSelfInfoResolver(resolverChain);
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return delegate.resolve();
    }
}