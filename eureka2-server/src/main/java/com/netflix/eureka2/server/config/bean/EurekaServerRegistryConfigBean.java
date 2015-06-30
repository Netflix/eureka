package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.server.config.EurekaServerRegistryConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaServerRegistryConfigBean implements EurekaServerRegistryConfig {

    private final long evictionTimeoutMs;
    private final StrategyType evictionStrategyType;
    private final String evictionStrategyValue;
    private final int evictionAllowedPercentageDrop;

    public EurekaServerRegistryConfigBean(long evictionTimeoutMs, StrategyType evictionStrategyType,
                                          String evictionStrategyValue, int evictionAllowedPercentageDrop) {
        this.evictionTimeoutMs = evictionTimeoutMs;
        this.evictionStrategyType = evictionStrategyType;
        this.evictionStrategyValue = evictionStrategyValue;
        this.evictionAllowedPercentageDrop = evictionAllowedPercentageDrop;
    }

    @Override
    public long getEvictionTimeoutMs() {
        return evictionTimeoutMs;
    }

    @Override
    public StrategyType getEvictionStrategyType() {
        return evictionStrategyType;
    }

    @Override
    public String getEvictionStrategyValue() {
        return evictionStrategyValue;
    }

    @Override
    public int getEvictionAllowedPercentageDrop() {
        return evictionAllowedPercentageDrop;
    }

    public static Builder anEurekaServerRegistryConfig() {
        return new Builder();
    }

    public static class Builder {
        private long evictionTimeoutMs = DEFAULT_EVICTION_TIMEOUT_MS;
        private StrategyType evictionStrategyType = StrategyType.PercentageDrop;
        private String evictionStrategyValue = DEFAULT_EVICTION_STRATEGY_VALUE;
        private int evictionAllowedPercentageDrop = DEFAULT_EVICTION_ALLOWED_PERCENTAGE_DROP;

        private Builder() {
        }

        public Builder withEvictionTimeoutMs(long evictionTimeoutMs) {
            this.evictionTimeoutMs = evictionTimeoutMs;
            return this;
        }

        public Builder withEvictionStrategyType(StrategyType evictionStrategyType) {
            this.evictionStrategyType = evictionStrategyType;
            return this;
        }

        public Builder withEvictionStrategyValue(String evictionStrategyValue) {
            this.evictionStrategyValue = evictionStrategyValue;
            return this;
        }

        public Builder withEvictionAllowedPercentageDrop(int evictionAllowedPercentageDrop) {
            this.evictionAllowedPercentageDrop = evictionAllowedPercentageDrop;
            return this;
        }

        public Builder but() {
            return anEurekaServerRegistryConfig().withEvictionTimeoutMs(evictionTimeoutMs).withEvictionStrategyType(evictionStrategyType).withEvictionStrategyValue(evictionStrategyValue);
        }

        public EurekaServerRegistryConfigBean build() {
            EurekaServerRegistryConfigBean eurekaServerRegistryConfigBean = new EurekaServerRegistryConfigBean(evictionTimeoutMs,
                    evictionStrategyType, evictionStrategyValue, evictionAllowedPercentageDrop);
            return eurekaServerRegistryConfigBean;
        }
    }
}
