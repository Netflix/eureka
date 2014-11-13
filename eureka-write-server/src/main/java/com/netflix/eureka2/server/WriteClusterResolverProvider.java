package com.netflix.eureka2.server;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@Singleton
public class WriteClusterResolverProvider implements Provider<ServerResolver> {

    private final EurekaBootstrapConfig config;

    private ServerResolver resolver;

    @Inject
    public WriteClusterResolverProvider(EurekaBootstrapConfig config) {
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

            final int replicationPort = config.getReplicationPort();
            String[] writeClusterServers = config.getWriteClusterServers();
            switch (config.getResolverType()) {
                case "dns":
                    String writeServerDnsName = writeClusterServers[0];
                    resolver = ServerResolvers.forDnsName(writeServerDnsName, replicationPort);
                    break;
                case "inline":
                    ServerResolver.Server[] servers = new ServerResolver.Server[writeClusterServers.length];
                    for (int i = 0; i < writeClusterServers.length; i++) {
                        String serverHostName = writeClusterServers[i];
                        servers[i] = new ServerResolver.Server(serverHostName, replicationPort);
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
