package com.netflix.eureka2.config;

import static com.netflix.eureka2.config.ConfigurationNames.TransportNames.connectionAutoTimeoutMsName;
import static com.netflix.eureka2.config.ConfigurationNames.TransportNames.heartbeatIntervalMsName;

/**
 * See {@link EurekaTransportConfig} for documentation
 *
 * @author David Liu
 */
public class BasicEurekaTransportConfig implements EurekaTransportConfig {

    public static final long HEARTBEAT_INTERVAL_MS = 30 * 1000;
    public static final long CONNECTION_AUTO_TIMEOUT_MS = 30 * 60 * 1000;

    private final long heartbeatIntervalMs;
    private final long connectionAutoTimeoutMs;

    private BasicEurekaTransportConfig(long heartbeatIntervalMs,
                                       long connectionAutoTimeoutMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    @Override
    public long getConnectionAutoTimeoutMs() {
        return connectionAutoTimeoutMs;
    }

    @Override
    public String toString() {
        return "BasicEurekaTransportConfig{" +
                "heartbeatIntervalMs=" + heartbeatIntervalMs +
                ", connectionAutoTimeoutMs=" + connectionAutoTimeoutMs +
                '}';
    }

    public static class Builder {
        private long heartbeatIntervalMs = SystemConfigLoader
                .getFromSystemPropertySafe(heartbeatIntervalMsName, HEARTBEAT_INTERVAL_MS);
        private long connectionAutoTimeoutMs = SystemConfigLoader
                .getFromSystemPropertySafe(connectionAutoTimeoutMsName, CONNECTION_AUTO_TIMEOUT_MS);

        public Builder withHeartbeatIntervalMs(long heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
            return this;
        }

        public Builder withConnectionAutoTimeoutMs(long connectionAutoTimeoutMs) {
            this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
            return this;
        }

        public BasicEurekaTransportConfig build() {
            return new BasicEurekaTransportConfig(heartbeatIntervalMs, connectionAutoTimeoutMs);
        }
    }
}
