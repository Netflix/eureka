package com.netflix.eureka2;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.service.selfinfo.CachingSelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.ChainableSelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.ConfigSelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.PeriodicDataCenterInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolverChain;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class DashboardServerSelfInfoResolver implements SelfInfoResolver {

    public static final String WEB_SOCKET_SERVICE = "webSocket";

    private final SelfInfoResolver delegate;

    @Inject
    public DashboardServerSelfInfoResolver(final EurekaDashboardConfig config, final WebSocketServer webSocketServer) {

        SelfInfoResolverChain resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config.getEurekaInstance()),
                // dashboard server specific resolver
                new ChainableSelfInfoResolver(Observable.just(new HashSet<ServicePort>())
                        .map(new Func1<HashSet<ServicePort>, Builder>() {
                            @Override
                            public InstanceInfo.Builder call(HashSet<ServicePort> ports) {
                                ports.add(new ServicePort(WEB_SOCKET_SERVICE, webSocketServer.serverPort(), false));
                                return new InstanceInfo.Builder().withPorts(ports);
                            }
                        })
                ),
                new PeriodicDataCenterInfoResolver(config.getEurekaInstance(), config.getEurekaTransport()),
                // TODO override with more meaningful health check
                new ChainableSelfInfoResolver(Observable.just(new InstanceInfo.Builder().withStatus(InstanceInfo.Status.UP)))
        );

        this.delegate = new CachingSelfInfoResolver(resolverChain);
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        return delegate.resolve();
    }
}
