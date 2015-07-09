package com.netflix.eureka2.eureka1.rest.config;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author Tomasz Bak
 */
public interface Eureka1Configuration {
    @DefaultValue("30000")
    long getCacheRefreshIntervalMs();
}
