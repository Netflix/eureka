package com.netflix.eureka2.config;

/**
 * use camel case for better readability
 *
 * @author David Liu
 */
public final class ConfigurationNames {
    private static final String EUREKA_PREFIX = "eurekas.";

    public final class TransportNames {
        private static final String PREFIX = "transport.";

        public static final String heartbeatIntervalMsName = EUREKA_PREFIX + PREFIX + "heartbeatIntervalMs";
        public static final String connectionAutoTimeoutMsName = EUREKA_PREFIX + PREFIX + "connectionAutoTimeoutMs";
        public static final String codecName = EUREKA_PREFIX + PREFIX + "codec";
    }

    public final class RegistryNames {
        private static final String PREFIX = "registry.";

        public static final String evictionTimeoutMsName = EUREKA_PREFIX + PREFIX + "evictionTimeoutMs";
        public static final String evictionStrategyTypeName = EUREKA_PREFIX + PREFIX + "evictionStrategy.type";
        public static final String evictionStrategyValueName = EUREKA_PREFIX + PREFIX + "evictionStrategy.value";
    }
}
