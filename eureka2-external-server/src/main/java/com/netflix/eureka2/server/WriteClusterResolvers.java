package com.netflix.eureka2.server;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ResolverType;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ServerBootstrap;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public final class WriteClusterResolvers {

    private WriteClusterResolvers() {
    }

    public static ServerResolver createRegistrationResolver(EurekaCommonConfig config) {
        return createWriteServerResolver(config,
                new Func1<ServerBootstrap, Integer>() {
                    @Override
                    public Integer call(ServerBootstrap server) {
                        return server.getRegistrationPort();
                    }
                }
        );
    }

    public static ServerResolver createInterestResolver(EurekaCommonConfig config) {
        return createWriteServerResolver(config,
                new Func1<ServerBootstrap, Integer>() {
                    @Override
                    public Integer call(ServerBootstrap server) {
                        return server.getDiscoveryPort();
                    }
                }
        );
    }

    public static ServerResolver createWriteServerResolver(EurekaCommonConfig config, Func1<ServerBootstrap, Integer> getPortFunc) {
        EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
        if (resolverType == null) {
            throw new IllegalArgumentException("Write cluster resolver type not defined");
        }

        ServerResolver resolver;

        ServerBootstrap[] bootstraps = ServerBootstrap.from(config.getServerList());

        if (resolverType == ResolverType.dns) {
            resolver = forDNS(bootstraps, getPortFunc);
        } else {
            resolver = forFixed(bootstraps, getPortFunc);
        }
        return resolver;
    }

    private static ServerResolver forDNS(ServerBootstrap[] bootstraps, Func1<ServerBootstrap, Integer> getPortFunc) {
        if (bootstraps.length != 1) {
            throw new IllegalArgumentException("Expected one DNS name for server resolver, while got " + bootstraps.length);
        }
        return ServerResolvers.fromDnsName(bootstraps[0].getHostname()).withPort(getPortFunc.call(bootstraps[0]));
    }

    private static ServerResolver forFixed(ServerBootstrap[] bootstraps, Func1<ServerBootstrap, Integer> getPortFunc) {
        Server[] servers = new Server[bootstraps.length];
        for (int i = 0; i < bootstraps.length; i++) {
            servers[i] = new Server(bootstraps[i].getHostname(), getPortFunc.call(bootstraps[i]));
        }
        return ServerResolvers.from(servers);
    }
}
