package com.netflix.eureka2.client.resolver;

/**
 * @author David Liu
 */
public class WriteServerResolverSet {

    private final ServerResolver registrationBootstrap;
    private final ServerResolver discoveryBootstrap;

    private WriteServerResolverSet(ServerResolver registrationBootstrap, ServerResolver discoveryBootstrap) {
        this.registrationBootstrap = registrationBootstrap;
        this.discoveryBootstrap = discoveryBootstrap;
    }

    public static WriteServerResolverSet just(String bootstrapHostname, int registrationPort, int discoveryPort) {
        return new WriteServerResolverSet(
                ServerResolvers.just(bootstrapHostname, registrationPort),
                ServerResolvers.just(bootstrapHostname, discoveryPort)
        );
    }

    public static WriteServerResolverSet forDnsName(String bootstrapHostname, int registrationPort, int discoveryPort) {
        return new WriteServerResolverSet(
                ServerResolvers.forDnsName(bootstrapHostname, registrationPort),
                ServerResolvers.forDnsName(bootstrapHostname, discoveryPort)
        );
    }

    public ServerResolver forDiscovery() {
        return discoveryBootstrap;
    }

    public ServerResolver forRegistration() {
        return registrationBootstrap;
    }
}
