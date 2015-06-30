package com.netflix.eureka2.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.server.config.EurekaServerConfig;

/**
 * @author Tomasz Bak
 */
public interface EurekaDashboardConfig extends EurekaServerConfig {

    int DEFAULT_DASHBOARD_PORT = 7001;

    int DEFAULT_WEB_SOCKET_PORT = 9000;

    @DefaultValue("" + DEFAULT_DASHBOARD_PORT)
    int getDashboardPort();

    @DefaultValue("" + DEFAULT_WEB_SOCKET_PORT)
    int getWebSocketPort();
}
