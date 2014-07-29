package com.netflix.eureka.client.bootstrap;

import java.net.SocketAddress;
import java.util.List;

import com.netflix.eureka.client.BootstrapResolver;

/**
 * Resolve write cluster from DNS.
 *
 * @author Tomasz Bak
 */
public class DnsBootstrapResolver implements BootstrapResolver {
    @Override
    public List<SocketAddress> resolveWriteClusterServers() {
        return null;
    }
}
