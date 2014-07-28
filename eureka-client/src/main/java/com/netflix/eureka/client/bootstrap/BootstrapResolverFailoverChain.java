package com.netflix.eureka.client.bootstrap;

import java.net.SocketAddress;
import java.util.List;

import com.netflix.eureka.client.BootstrapResolver;

/**
 * Provide means to use multiple sources of bootstrap server list.
 *
 * @author Tomasz Bak
 */
public class BootstrapResolverFailoverChain implements BootstrapResolver {
    @Override
    public List<SocketAddress> resolveWriteClusterServers() {
        return null;
    }
}
