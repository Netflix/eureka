package com.netflix.eureka.client.bootstrap;

import java.net.SocketAddress;
import java.util.List;

import com.netflix.eureka.client.BootstrapResolver;

/**
 * A list of server addresses is read from a local configuration file.
 *
 * @author Tomasz Bak
 */
public class FileBootstrapResolver implements BootstrapResolver {
    @Override
    public List<SocketAddress> resolveWriteClusterServers() {
        return null;
    }
}
