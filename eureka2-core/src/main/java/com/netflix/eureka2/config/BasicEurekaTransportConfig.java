package com.netflix.eureka2.config;

import com.netflix.eureka2.codec.CodecType;

import static com.netflix.eureka2.config.ConfigurationNames.TransportNames.codecName;
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
    public static final CodecType DEFAULT_CODEC = CodecType.Avro;

    private final long heartbeatIntervalMs;
    private final long connectionAutoTimeoutMs;
    private final CodecType codec;

    private BasicEurekaTransportConfig(long heartbeatIntervalMs,
                                       long connectionAutoTimeoutMs,
                                       CodecType codec) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
        this.codec = codec;
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
    public CodecType getCodec() {
        return codec;
    }

    @Override
    public String toString() {
        return "BasicEurekaTransportConfig{" +
                "heartbeatIntervalMs=" + heartbeatIntervalMs +
                ", connectionAutoTimeoutMs=" + connectionAutoTimeoutMs +
                ", codec=" + codec +
                '}';
    }

    public static class Builder {
        private long heartbeatIntervalMs = SystemConfigLoader
                .getFromSystemPropertySafe(heartbeatIntervalMsName, HEARTBEAT_INTERVAL_MS);
        private long connectionAutoTimeoutMs = SystemConfigLoader
                .getFromSystemPropertySafe(connectionAutoTimeoutMsName, CONNECTION_AUTO_TIMEOUT_MS);
        private CodecType codec = SystemConfigLoader
                .getFromSystemPropertySafe(codecName, DEFAULT_CODEC);

        public Builder withHeartbeatIntervalMs(long heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
            return this;
        }

        public Builder withConnectionAutoTimeoutMs(long connectionAutoTimeoutMs) {
            this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
            return this;
        }

        public Builder withCodec(CodecType codec) {
            this.codec = codec;
            return this;
        }

        public BasicEurekaTransportConfig build() {
            return new BasicEurekaTransportConfig(heartbeatIntervalMs, connectionAutoTimeoutMs, codec);
        }
    }
}
