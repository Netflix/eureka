package com.netflix.eureka2.config;

import static com.netflix.eureka2.config.ConfigurationNames.RegistryNames.evictionAllowedPercentageDropName;

/**
 * basic eureka registry config that reads properties from System.properties if available,
 * but also allows programmatic overrides and provides some defaults.
 * @author David Liu
 */
public class BasicEurekaRegistryConfig implements EurekaRegistryConfig {

    public static final int EVICTION_ALLOWED_PERCENTAGE_DROP = 20;

    private final int evictionAllowedPercentageDrop;

    private BasicEurekaRegistryConfig(int evictionAllowedPercentageDrop) {
        this.evictionAllowedPercentageDrop = evictionAllowedPercentageDrop;
    }

    @Override
    public int getEvictionAllowedPercentageDrop() {
        return evictionAllowedPercentageDrop;
    }

    @Override
    public String toString() {
        return "BasicEurekaRegistryConfig{" +
                "evictionAllowedPercentageDrop=" + evictionAllowedPercentageDrop +
                '}';
    }

    public static class Builder {
        private int evictionAllowedPercentageDrop = SystemConfigLoader.
                getFromSystemPropertySafe(evictionAllowedPercentageDropName, EVICTION_ALLOWED_PERCENTAGE_DROP);

        public Builder withEvictionAllowedPercentageDrop(int evictionAllowedPercentageDrop) {
            this.evictionAllowedPercentageDrop = evictionAllowedPercentageDrop;
            return this;
        }

        public BasicEurekaRegistryConfig build() {
            return new BasicEurekaRegistryConfig(
                    evictionAllowedPercentageDrop);
        }
    }
}
