package com.netflix.eureka2;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.service.ChainableSelfInfoResolver;
import com.netflix.eureka2.server.service.ConfigSelfInfoResolver;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfInfoResolverChain;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@Singleton
public class DashboardServerSelfInfoResolver implements SelfInfoResolver {

    public static final String WEB_SOCKET_SERVICE = "webSocket";

    private SelfInfoResolverChain resolverChain;

    @Inject
    public DashboardServerSelfInfoResolver(final EurekaCommonConfig config, final WebSocketServer webSocketServer) {

        resolverChain = new SelfInfoResolverChain(
                new ConfigSelfInfoResolver(config),
                new ChainableSelfInfoResolver() {  // dashboard server specific resolver
                    @Override
                    protected Observable<Builder> resolveMutable() {
                        HashSet<ServicePort> ports = new HashSet<>();
                        ports.add(new ServicePort(WEB_SOCKET_SERVICE, webSocketServer.serverPort(), false));

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
