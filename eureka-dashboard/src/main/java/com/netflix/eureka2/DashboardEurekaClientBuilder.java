package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ResolverType;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ServerBootstrap;

@Singleton
public class DashboardEurekaClientBuilder {

    private final EurekaClient eurekaClient;

    @Inject
    public DashboardEurekaClientBuilder(EurekaDashboardConfig config) {
        ServerResolver resolver;

        EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
        EurekaCommonConfig.ServerBootstrap[] bootstraps = EurekaCommonConfig.ServerBootstrap.from(config.getServerList());

        if (resolverType == ResolverType.dns) {
            resolver = forDNS(bootstraps);
        } else {
            resolver = forFixed(bootstraps);
        }
        eurekaClient = Eureka.newClientBuilder(resolver).build();
    }

    public EurekaClient getEurekaClient() {
        return eurekaClient;
    }

    private static ServerResolver forDNS(ServerBootstrap[] bootstraps) {
        if (bootstraps.length != 1) {
            throw new IllegalArgumentException("Expected one DNS name for server resolver, while got " + bootstraps.length);
        }
        return ServerResolvers.forDnsName(bootstraps[0].getHostname(), bootstraps[0].getDiscoveryPort());
    }

    private static ServerResolver forFixed(ServerBootstrap[] bootstraps) {
        Server[] servers = new Server[bootstraps.length];
        for (int i = 0; i < bootstraps.length; i++) {
            servers[i] = new Server(bootstraps[i].getHostname(), bootstraps[i].getDiscoveryPort());
        }
        return ServerResolvers.from(servers);
    }
}
