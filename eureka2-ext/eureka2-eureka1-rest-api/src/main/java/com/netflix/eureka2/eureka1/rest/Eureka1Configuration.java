package com.netflix.eureka2.eureka1.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class Eureka1Configuration {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1Configuration.class);

    public static final String REFRESH_INTERVAL_KEY = "eureka.ext.eureka1.rest.refreshInterval";
    public static final String QUERY_TIMEOUT_KEY = "eureka.ext.eureka1.rest.queryTimeout";

    public static final long DEFAULT_REFRESH_INTERVAL_MS = 30000;
    private static final long DEFAULT_QUERY_TIMEOUT = 30000;

    private final long refreshIntervalMs;
    private final long queryTimeout;

    public Eureka1Configuration() {
        this.refreshIntervalMs = getLongProperty(REFRESH_INTERVAL_KEY, DEFAULT_REFRESH_INTERVAL_MS);
        this.queryTimeout = getLongProperty(QUERY_TIMEOUT_KEY, DEFAULT_QUERY_TIMEOUT);
    }

    public Eureka1Configuration(long refreshIntervalMs, long queryTimeout) {
        this.refreshIntervalMs = refreshIntervalMs;
        this.queryTimeout = queryTimeout;
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public long getQueryTimeout() {
        return queryTimeout;
    }

    private static long getLongProperty(String key, long defaultValue) {
        String value = System.getProperty(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                logger.warn("Invalid {} property value {}; defaulting to ", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
}
