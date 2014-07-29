package com.netflix.eureka.client;

import java.net.SocketAddress;
import java.util.List;

/**
 * Discovery of Eureka write cluster servers.
 *
 * @author Tomasz Bak
 */
public interface BootstrapResolver {
    List<SocketAddress> resolveWriteClusterServers();
}
