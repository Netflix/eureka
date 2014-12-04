package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.config.EurekaCommonConfig;

@Singleton
public class DashboardEurekaClientBuilder {

    private final EurekaClient eurekaClient;

    @Inject
    public DashboardEurekaClientBuilder(EurekaDashboardConfig config) {
        ServerResolver resolver;

        EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
        EurekaCommonConfig.ServerBootstrap[] bootstraps = EurekaCommonConfig.ServerBootstrap.from(config.getServerList());
        ServerResolver[] resolvers = new ServerResolver[bootstraps.length];

        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i] = resolveForType(bootstraps[i], resolverType);
        }

        resolver = ServerResolvers.from(resolvers);

        eurekaClient = Eureka.newClientBuilder(resolver).build();
    }

    public EurekaClient getEurekaClient() {
        return eurekaClient;
    }

    private ServerResolver resolveForType(EurekaCommonConfig.ServerBootstrap bootstrap, EurekaCommonConfig.ResolverType resolverType) {
        if (resolverType == null) {
            throw new IllegalArgumentException("Write cluster resolver type not defined");
        }

        ServerResolver resolver;
        switch (resolverType) {
            case dns:
                resolver = ServerResolvers.forDnsName(bootstrap.getHostname(), bootstrap.getDiscoveryPort());
                break;
            case fixed:
                resolver = ServerResolvers.just(bootstrap.getHostname(), bootstrap.getDiscoveryPort());
                break;
            default:
                throw new IllegalArgumentException("Unrecognized write cluster resolver");
        }
        return resolver;
    }
}
