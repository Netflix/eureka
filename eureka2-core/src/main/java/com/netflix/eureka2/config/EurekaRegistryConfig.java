package com.netflix.eureka2.config;

import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;

/**
 * @author David Liu
 */
public interface EurekaRegistryConfig {

    long getEvictionTimeoutMs();

    @Deprecated
    StrategyType getEvictionStrategyType();

    @Deprecated
    String getEvictionStrategyValue();

    int getEvictionAllowedPercentageDrop();
}
