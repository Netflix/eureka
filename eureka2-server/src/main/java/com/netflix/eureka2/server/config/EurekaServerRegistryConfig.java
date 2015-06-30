package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;

/**
 * This interface exists so we can provide default values for configuration parameters. These annotations
 * cannot be added to {@link EurekaRegistryConfig}, as it resides in eureka-client module, which has no
 * dependency to Archaius.
 *
 * @author Tomasz Bak
 */
public interface EurekaServerRegistryConfig extends EurekaRegistryConfig {

    long DEFAULT_EVICTION_TIMEOUT_MS = 30000;

    String DEFAULT_EVICTION_STRATEGY_VALUE = "80";

    int DEFAULT_EVICTION_ALLOWED_PERCENTAGE_DROP = 20;

    @Override
    @DefaultValue("" + DEFAULT_EVICTION_TIMEOUT_MS)
    long getEvictionTimeoutMs();

    @Override
    @DefaultValue("PercentageDrop")
    StrategyType getEvictionStrategyType();

    // FIXME This property will most likely be not needed after eviction refactorings are completed
    @Override
    @DefaultValue(DEFAULT_EVICTION_STRATEGY_VALUE)
    String getEvictionStrategyValue();

    @Override
    @DefaultValue("" + DEFAULT_EVICTION_ALLOWED_PERCENTAGE_DROP)
    int getEvictionAllowedPercentageDrop();
}
