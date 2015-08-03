package com.netflix.eureka2.eureka1.bridge.config.bean;

import com.netflix.eureka2.eureka1.bridge.config.Eureka1BridgeConfiguration;

/**
 * @author Tomasz Bak
 */
public class Eureka1BridgeConfigurationBean implements Eureka1BridgeConfiguration {
    private final long refreshRateSec;

    public Eureka1BridgeConfigurationBean(long refreshRateSec) {
        this.refreshRateSec = refreshRateSec;
    }

    @Override
    public long getRefreshRateSec() {
        return refreshRateSec;
    }

    public static Builder anEureka1BridgeConfiguration() {
        return new Builder();
    }

    public static class Builder {
        private long refreshRateSec;

        private Builder() {
        }

        public Builder withRefreshRateSec(long refreshRateSec) {
            this.refreshRateSec = refreshRateSec;
            return this;
        }

        public Builder but() {
            return anEureka1BridgeConfiguration().withRefreshRateSec(refreshRateSec);
        }

        public Eureka1BridgeConfigurationBean build() {
            Eureka1BridgeConfigurationBean eureka1BridgeConfigurationBean = new Eureka1BridgeConfigurationBean(refreshRateSec);
            return eureka1BridgeConfigurationBean;
        }
    }
}
