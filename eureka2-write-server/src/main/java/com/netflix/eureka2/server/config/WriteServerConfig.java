package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;

/**
 * @author Tomasz Bak
 */
public interface WriteServerConfig extends EurekaServerConfig {

    int DEFAULT_REPLICATION_RECONNECT_DELAY_MS = 500;

    boolean DEFAULT_BOOTSTRAP_ENABLED = false;

    long DEFAULT_BOOTSTRAP_TIMEOUT_MS = 30000;

    @DefaultValue("" + DEFAULT_REPLICATION_RECONNECT_DELAY_MS)
    long getReplicationReconnectDelayMs();

    @DefaultValue("" + DEFAULT_BOOTSTRAP_ENABLED)
    boolean isBootstrapEnabled();

    @DefaultValue("Fixed")
    ResolverType getBootstrapResolverType();

    ClusterAddress[] getBootstrapClusterAddresses();

    @DefaultValue("" + DEFAULT_BOOTSTRAP_TIMEOUT_MS)
    long getBootstrapTimeoutMs();
}
