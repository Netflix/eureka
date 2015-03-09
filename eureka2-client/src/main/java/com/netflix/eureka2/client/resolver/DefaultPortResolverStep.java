package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;

/**
 * @author David Liu
 */
class DefaultPortResolverStep implements PortResolverStep {
    private final int port;

    DefaultPortResolverStep(int port) {
        this.port = port;
    }

    @Override
    public ServerResolver withHostname(final String hostname) {
        return ServerResolvers.from(new Server(hostname, port));
    }

    @Override
    public ServerResolver withDnsName(String dnsName) {
        return ServerResolvers.withDnsName(dnsName).withPort(port);
    }
}
