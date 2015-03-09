package com.netflix.eureka2.client.resolver;

/**
 * @author David Liu
 */
public interface PortResolverStep extends ServerResolverStep {

    ServerResolver withHostname(String hostname);

    ServerResolver withDnsName(String dnsName);
}
