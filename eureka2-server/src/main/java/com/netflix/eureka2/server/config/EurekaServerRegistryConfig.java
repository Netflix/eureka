package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.config.EurekaRegistryConfig;

/**
 * This interface exists so we can provide default values for configuration parameters. These annotations
 * cannot be added to {@link EurekaRegistryConfig}, as it resides in eureka-client module, which has no
 * dependency to Archaius.
 *
 * @author Tomasz Bak
 */
public interface EurekaServerRegistryConfig extends EurekaRegistryConfig {

    int DEFAULT_EVICTION_ALLOWED_PERCENTAGE_DROP = 20;

    @Override
    @DefaultValue("" + DEFAULT_EVICTION_ALLOWED_PERCENTAGE_DROP)
    int getEvictionAllowedPercentageDrop();
}
