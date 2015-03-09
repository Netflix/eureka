package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;

/**
 * @author David Liu
 */
class FixedHostResolverStep implements HostResolverStep {

    private final String hostname;

    FixedHostResolverStep(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public ServerResolver withPort(final int port) {
        return ServerResolvers.from(new Server(hostname, port));
    }
}
