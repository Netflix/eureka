package com.netflix.eureka2.client.resolver;

/**
 * @author David Liu
 */
public interface HostResolverStep {

    public ServerResolver withPort(int port);

}
