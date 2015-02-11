package com.netflix.eureka2.server.service;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
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
            final EurekaServerConfig config,
            final TcpRegistrationServer registrationServer,
            final TcpReplicationServer replicationServer,
            final TcpDiscoveryServer discoveryServer)
    {
        SelfInfoResolverChain resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config),
                // write server specific resolver
                new ChainableSelfInfoResolver(Observable.just(new HashSet<ServicePort>())
                        .map(new Func1<HashSet<ServicePort>, InstanceInfo.Builder>() {
                            @Override
                            public InstanceInfo.Builder call(HashSet<ServicePort> ports) {
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