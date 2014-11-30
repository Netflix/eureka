package com.netflix.eureka2.server;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@Singleton
public class WriteClusterResolverProvider implements Provider<ServerResolver> {

    private final EurekaServerConfig config;

    private ServerResolver resolver;

    @Inject
    public WriteClusterResolverProvider(EurekaServerConfig config) {
        this.config = config;
    }

    public WriteClusterResolverProvider(ServerResolver resolver) {
        this.config = null;
        this.resolver = resolver;
    }

    @PostConstruct
    public void createResolver() {
        if (resolver == null) {
            EurekaCommonConfig.ResolverType resolverType = config.getServerResolverType();
            EurekaCommonConfig.ServerBootstrap[] bootstraps = EurekaCommonConfig.ServerBootstrap.from(config.getServerList());
            ServerResolver[] resolvers = new ServerResolver[bootstraps.length];

            for (int i = 0; i < resolvers.length; i++) {
                resolvers[i] = resolveForType(bootstraps[i], resolverType);
            }

            resolver = ServerResolvers.from(resolvers);
        }
    }

    @Override
    public ServerResolver get() {
        return resolver;
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
