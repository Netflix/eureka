package com.netflix.eureka2.client.resolver;

/**
 * @author David Liu
 */
public interface PortResolverStep {

    public ServerResolver withHostname(String hostname);

    public ServerResolver withDnsName(String dnsName);
}
