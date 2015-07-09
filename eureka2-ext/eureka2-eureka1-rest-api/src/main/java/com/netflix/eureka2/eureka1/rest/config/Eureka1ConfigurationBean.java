package com.netflix.eureka2.eureka1.rest.config;

/**
 * @author Tomasz Bak
 */
public class Eureka1ConfigurationBean implements Eureka1Configuration {
    private final long cacheRefreshIntervalMs;

    public Eureka1ConfigurationBean(long cacheRefreshIntervalMs) {
        this.cacheRefreshIntervalMs = cacheRefreshIntervalMs;
    }

    @Override
    public long getCacheRefreshIntervalMs() {
        return cacheRefreshIntervalMs;
    }

    public static Eureka1ConfigurationBeanBuilder anEureka1ConfigurationBean() {
        return new Eureka1ConfigurationBeanBuilder();
    }

    public static class Eureka1ConfigurationBeanBuilder {
        private long cacheRefreshIntervalMs;

        private Eureka1ConfigurationBeanBuilder() {
        }

        public Eureka1ConfigurationBeanBuilder withCacheRefreshIntervalMs(long cacheRefreshIntervalMs) {
            this.cacheRefreshIntervalMs = cacheRefreshIntervalMs;
            return this;
        }

        public Eureka1ConfigurationBeanBuilder but() {
            return anEureka1ConfigurationBean().withCacheRefreshIntervalMs(cacheRefreshIntervalMs);
        }

        public Eureka1ConfigurationBean build() {
            return new Eureka1ConfigurationBean(cacheRefreshIntervalMs);
        }
    }
}
