package com.netflix.eureka2.eureka1.bridge.config;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author Tomasz Bak
 */
public interface Eureka1BridgeConfiguration {
    @DefaultValue("30")
    long getRefreshRateSec();
}
