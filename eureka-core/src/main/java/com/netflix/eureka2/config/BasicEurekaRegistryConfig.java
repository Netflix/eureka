package com.netflix.eureka2.config;

import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;

/**
 * basic eureka registry config that reads properties from System.properties if available,
 * but also allows programmatic overrides and provides some defaults.
 * @author David Liu
 */
public class BasicEurekaRegistryConfig implements EurekaRegistryConfig {

    private static final String EVICTION_TIMEOUT_MS = "30000";
    private static final String EVICTION_STRATEGY_TYPE = StrategyType.PercentageDrop.name();
    private static final String EVICTION_STRATEGY_VALUE = "20";

    private String evictionTimeoutMs = System.getProperty("eureka.registry.evictionTimeoutMs", EVICTION_TIMEOUT_MS);
    private String evictionStrategyType = System.getProperty("eureka.registry.evictionStrategy.type", EVICTION_STRATEGY_TYPE);
    private String evictionStrategyValue = System.getProperty("eureka.registry.evictionStrategy.value", EVICTION_STRATEGY_VALUE);

    public BasicEurekaRegistryConfig() {
        this(null, null, null);
    }

    public BasicEurekaRegistryConfig(Long evictionTimeoutMs, StrategyType evictionStrategyType, String evictionStrategyValue) {
        this.evictionTimeoutMs = evictionTimeoutMs == null ? this.evictionTimeoutMs : evictionTimeoutMs.toString();
        this.evictionStrategyType = evictionStrategyType == null ? this.evictionStrategyType : evictionStrategyType.name();
        this.evictionStrategyValue = evictionStrategyValue == null ? this.evictionStrategyValue : evictionStrategyValue;
    }

    @Override
    public long getEvictionTimeoutMs() {
        return Long.parseLong(evictionTimeoutMs);
    }

    @Override
    public StrategyType getEvictionStrategyType() {
        return StrategyType.valueOf(evictionStrategyType);
    }

    @Override
    public String getEvictionStrategyValue() {
        return evictionStrategyValue;
    }

    @Override
    public String toString() {
        return "BasicEurekaRegistryConfig{" +
                "evictionTimeoutMs='" + evictionTimeoutMs + '\'' +
                ", evictionStrategyType='" + evictionStrategyType + '\'' +
                ", evictionStrategyValue='" + evictionStrategyValue + '\'' +
                '}';
    }
}
