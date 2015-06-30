package com.netflix.eureka2.server.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

import com.google.inject.Provider;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.http.EurekaHttpServer;
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
            final Provider<TcpInterestServer> discoveryServer) {
        SelfInfoResolverChain resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config.getEurekaInstance(), config.getEurekaTransport()),
                // write server specific resolver
                new ChainableSelfInfoResolver(Observable.just(new HashSet<ServicePort>())
                        .map(new Func1<HashSet<ServicePort>, InstanceInfo.Builder>() {
                            @Override
                            public InstanceInfo.Builder call(HashSet<ServicePort> ports) {
                                ports.add(new ServicePort(Names.EUREKA_HTTP, httpServer.serverPort(), false));
                                ports.add(new ServicePort(Names.REGISTRATION, registrationServer.get().serverPort(), false));
                                ports.add(new ServicePort(Names.REPLICATION, replicationServer.get().serverPort(), false));
                                ports.add(new ServicePort(Names.INTEREST, discoveryServer.get().serverPort(), false));

                                return new InstanceInfo.Builder().withPorts(ports);
                            }
                        })
                ),
                new PeriodicDataCenterInfoResolver(config.getEurekaInstance()),
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