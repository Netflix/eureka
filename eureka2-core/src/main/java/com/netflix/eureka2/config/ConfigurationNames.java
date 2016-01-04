package com.netflix.eureka2.config;

/**
 * use camel case for better readability
 *
 * @author David Liu
 */
public final class ConfigurationNames {
    private static final String EUREKA_PREFIX = "eureka.";

    public final class TransportNames {
        private static final String PREFIX = "transport.";

        public static final String heartbeatIntervalMsName = EUREKA_PREFIX + PREFIX + "heartbeatIntervalMs";
        public static final String connectionAutoTimeoutMsName = EUREKA_PREFIX + PREFIX + "connectionAutoTimeoutMs";
    }

    public final class RegistryNames {
        private static final String PREFIX = "registry.";

        public static final String evictionAllowedPercentageDropName = EUREKA_PREFIX + PREFIX + "evictionAllowedPercentageDrop";
    }
}
