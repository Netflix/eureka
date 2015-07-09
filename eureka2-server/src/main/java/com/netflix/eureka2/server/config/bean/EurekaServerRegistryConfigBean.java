package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.EurekaServerRegistryConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaServerRegistryConfigBean implements EurekaServerRegistryConfig {

    private final int evictionAllowedPercentageDrop;

    public EurekaServerRegistryConfigBean(int evictionAllowedPercentageDrop) {
        this.evictionAllowedPercentageDrop = evictionAllowedPercentageDrop;
    }

    public static Builder anEurekaServerRegistryConfig() {
        return new Builder();
    }

    @Override
    public int getEvictionAllowedPercentageDrop() {
        return evictionAllowedPercentageDrop;
    }

    public static class Builder {
        private int evictionAllowedPercentageDrop = DEFAULT_EVICTION_ALLOWED_PERCENTAGE_DROP;

        private Builder() {
        }

        public Builder withEvictionAllowedPercentageDrop(int evictionAllowedPercentageDrop) {
            this.evictionAllowedPercentageDrop = evictionAllowedPercentageDrop;
            return this;
        }

        public Builder but() {
            return anEurekaServerRegistryConfig().withEvictionAllowedPercentageDrop(evictionAllowedPercentageDrop);
        }

        public EurekaServerRegistryConfigBean build() {
            return new EurekaServerRegistryConfigBean(evictionAllowedPercentageDrop);
        }
    }
}
