package com.netflix.eureka2.config;

import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;

/**
 * @author David Liu
 */
public interface EurekaRegistryConfig {

    public long getEvictionTimeoutMs();

    public StrategyType getEvictionStrategyType();

    public String getEvictionStrategyValue();
}
