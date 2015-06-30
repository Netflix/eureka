package com.netflix.eureka2.server.config;

/**
 * @author Tomasz Bak
 */
public interface EurekaServerConfig {
    EurekaClusterDiscoveryConfig getEurekaClusterDiscovery();

    EurekaInstanceInfoConfig getEurekaInstance();

    EurekaServerTransportConfig getEurekaTransport();

    EurekaServerRegistryConfig getEurekaRegistry();
}
