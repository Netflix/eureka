package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author Tomasz Bak
 */
public interface WriteServerConfig extends EurekaServerConfig {

    int DEFAULT_REPLICATION_RECONNECT_DELAY_MS = 500;

    @DefaultValue("" + DEFAULT_REPLICATION_RECONNECT_DELAY_MS)
    long getReplicationReconnectDelayMs();

    BootstrapConfig getBootstrap();
}
