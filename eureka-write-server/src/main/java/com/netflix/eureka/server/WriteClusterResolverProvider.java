package com.netflix.eureka.server;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.Protocol;
import com.netflix.eureka.client.ServerResolver.ProtocolType;
import com.netflix.eureka.client.bootstrap.ServerResolvers;

import static java.util.Arrays.*;

/**
 * @author Tomasz Bak
 */
@Singleton
public class WriteClusterResolverProvider implements Provider<ServerResolver<InetSocketAddress>> {

    private final EurekaBootstrapConfig config;

    private ServerResolver<InetSocketAddress> resolver;

    @Inject
    public WriteClusterResolverProvider(EurekaBootstrapConfig config) {
        this.config = config;
    }

    public WriteClusterResolverProvider(ServerResolver<InetSocketAddress> resolver) {
        this.config = null;
        this.resolver = resolver;
    }

    @PostConstruct
    public void createResolver() {
        if (resolver == null) {
            Protocol[] protocols = {
                    new Protocol(config.getReplicationPort(), ProtocolType.TcpReplication)
            };

            if (config.getResolverType() == null) {
                throw new IllegalArgumentException("Write cluster resolver type node defined");
            }

            switch (config.getResolverType()) {
                case "dns":
                    resolver = ServerResolvers.forDomainName(config.getWriteClusterServers()[0], protocols);
                    break;
                case "inline":
                    Set<Protocol> protocolSet = new HashSet<>(asList(protocols));
                    resolver = ServerResolvers.fromList(protocolSet, config.getWriteClusterServers());
                    break;
                default:
                    throw new IllegalArgumentException("Unrecognized write cluster resolver");
            }
        }
    }

    @Override
    public ServerResolver<InetSocketAddress> get() {
        return resolver;
    }
}
