package com.netflix.eureka2.client.resolver;

/**
 * @author David Liu
 */
public interface HostResolverStep extends ServerResolverStep {

    ServerResolver withPort(int port);

}
