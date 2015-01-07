package com.netflix.eureka2.server.service;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

/**
 * @author David Liu
 */
@Singleton
public class EurekaReadServerSelfInfoResolver implements SelfInfoResolver {

    private SelfInfoResolverChain resolverChain;

    @Inject
    public EurekaReadServerSelfInfoResolver(final EurekaServerConfig config, final TcpDiscoveryServer discoveryServer) {

        resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config),
                new ChainableSelfInfoResolver() {  // read server specific resolver
                    @Override
                    protected Observable<InstanceInfo.Builder> resolveMutable() {
                        HashSet<ServicePort> ports = new HashSet<>();
                        ports.add(new ServicePort(Names.DISCOVERY, discoveryServer.serverPort(), false));

                        return Observable.just(new InstanceInfo.Builder().withPorts(ports));
                    }
                },
                new ChainableSelfInfoResolver() {  // TODO override with more meaningful health check
                    @Override
                    protected Observable<InstanceInfo.Builder> resolveMutable() {
                        return Observable.just(new InstanceInfo.Builder().withStatus(InstanceInfo.Status.UP));
                    }
                }
        );
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return resolverChain.resolve();
    }
}
