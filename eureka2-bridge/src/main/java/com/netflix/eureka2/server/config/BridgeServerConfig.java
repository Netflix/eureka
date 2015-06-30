package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author Tomasz Bak
 */
public interface BridgeServerConfig extends WriteServerConfig {

    @DefaultValue("30")
    int getRefreshRateSec();
}
