package com.netflix.eureka.client.bootstrap;

import java.net.SocketAddress;
import java.util.List;

import com.netflix.eureka.client.BootstrapResolver;

/**
 * Read up to date bootstrap server list from the cluster itself.
 * Does it make sense????
 *
 * @author Tomasz Bak
 */
public class EurekaBootstrapResolver implements BootstrapResolver {
    @Override
    public List<SocketAddress> resolveWriteClusterServers() {
        return null;
    }
}
