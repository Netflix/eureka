package com.netflix.eureka2.config;

import com.netflix.eureka2.transport.EurekaTransports;

import static com.netflix.eureka2.config.ConfigurationNames.TransportNames.*;

/**
 * @author David Liu
 */
public class BasicEurekaTransportConfig implements EurekaTransportConfig {

    public static final long CONNECTION_AUTO_TIMEOUT_MS = 30*60*1000;
    public static final EurekaTransports.Codec DEFAULT_CODEC = EurekaTransports.Codec.Avro;

    private final Long connectionAutoTimeoutMs;
    private final EurekaTransports.Codec codec;

    private BasicEurekaTransportConfig(Long connectionAutoTimeoutMs, EurekaTransports.Codec codec) {
        this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
        this.codec = codec;
    }

    @Override
    public long getConnectionAutoTimeoutMs() {
        return connectionAutoTimeoutMs;
    }

    @Override
    public EurekaTransports.Codec getCodec() {
        return codec;
    }

    @Override
    public String toString() {
        return "BasicEurekaTransportConfig{" +
                "connectionAutoTimeoutMs='" + connectionAutoTimeoutMs + '\'' +
                ", codec='" + codec + '\'' +
                '}';
    }


    public static class Builder {
        private Long connectionAutoTimeoutMs = SystemConfigLoader
                .getFromSystemPropertySafe(connectionAutoTimeoutMsName, CONNECTION_AUTO_TIMEOUT_MS);
        private EurekaTransports.Codec codec = SystemConfigLoader
                .getFromSystemPropertySafe(codecName, DEFAULT_CODEC);

        public Builder withConnectionAutoTimeoutMs(Long connectionAutoTimeoutMs) {
            this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
            return this;
        }

        public Builder withCodec(EurekaTransports.Codec codec) {
            this.codec = codec;
            return this;
        }

        public BasicEurekaTransportConfig build() {
            return new BasicEurekaTransportConfig(connectionAutoTimeoutMs, codec);
        }
    }

}
