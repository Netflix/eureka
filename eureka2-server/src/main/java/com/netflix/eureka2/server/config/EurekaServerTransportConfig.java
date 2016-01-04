package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.config.EurekaTransportConfig;

/**
 * @author Tomasz Bak
 */
public interface EurekaServerTransportConfig extends EurekaTransportConfig {

    int DEFAULT_HTTP_PORT = 8080;
    int DEFAULT_SHUTDOWN_PORT = 7700;
    int DEFAULT_WEB_ADMIN_PORT = 8077;
    int DEFAULT_SERVER_PORT = 12102;
    long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000;
    long DEFAULT_CONNECTION_AUTO_TIMEOUT_MS = 30 * 60 * 1000;

    @DefaultValue("" + DEFAULT_HTTP_PORT)
    int getHttpPort();

    @DefaultValue("" + DEFAULT_SHUTDOWN_PORT)
    int getShutDownPort();

    @DefaultValue("" + DEFAULT_WEB_ADMIN_PORT)
    int getWebAdminPort();

    @DefaultValue("" + DEFAULT_SERVER_PORT)
    int getServerPort();

    @Override
    @DefaultValue("" + DEFAULT_HEARTBEAT_INTERVAL_MS)
    long getHeartbeatIntervalMs();

    @Override
    @DefaultValue("" + DEFAULT_CONNECTION_AUTO_TIMEOUT_MS)
    long getConnectionAutoTimeoutMs();
}
