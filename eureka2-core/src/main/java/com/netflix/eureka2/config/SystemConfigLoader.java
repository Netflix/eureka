package com.netflix.eureka2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * helpers for loading config from system properties that gracefully fallback to defaults.
 *
 * @author David Liu
 */
public final class SystemConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(SystemConfigLoader.class);

    private SystemConfigLoader() {
    }

    public static long getFromSystemPropertySafe(String key, long defaultValue) {
        long result;
        try {
            result = Long.parseLong(System.getProperty(key, ""+defaultValue));
        } catch (Exception e) {
            logger.warn("Error loading system property {}. Using a default {}", key, defaultValue);
            result = defaultValue;
        }

        return result;
    }

    public static int getFromSystemPropertySafe(String key, int defaultValue) {
        int result;
        try {
            result = Integer.parseInt(System.getProperty(key, ""+defaultValue));
        } catch (Exception e) {
            logger.warn("Error loading system property {}. Using a default {}", key, defaultValue);
            result = defaultValue;
        }

        return result;
    }
}
