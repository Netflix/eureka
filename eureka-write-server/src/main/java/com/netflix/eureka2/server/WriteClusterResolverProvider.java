package com.netflix.eureka2.server;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.config.EurekaBootstrapConfig;
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

            if (config.getResolverType() == null) {
                throw new IllegalArgumentException("Write cluster resolver type not defined");
            }

            switch (config.getResolverType()) {
                case "dns":
                    EurekaBootstrapConfig.WriteServerBootstrap server = config.getWriteClusterServerDns();
                    resolver = ServerResolvers.forDnsName(server.getHostname(), server.getReplicationPort());
                    break;
                case "inline":
                    EurekaBootstrapConfig.WriteServerBootstrap[] serverBootstraps = config.getWriteClusterServersInline();
                    ServerResolver.Server[] servers = new ServerResolver.Server[serverBootstraps.length];
                    for (int i = 0; i < servers.length; i++) {
                        servers[i] = new ServerResolver.Server(
                                serverBootstraps[i].getHostname(),
                                serverBootstraps[i].getReplicationPort()
                        );
                    }
                    resolver = ServerResolvers.from(servers);
                    break;
                default:
                    throw new IllegalArgumentException("Unrecognized write cluster resolver");
            }
        }
    }

    @Override
    public ServerResolver get() {
        return resolver;
    }
}
