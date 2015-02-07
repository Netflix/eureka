package com.netflix.eureka2.config;

import com.netflix.eureka2.transport.EurekaTransports;

import static com.netflix.eureka2.config.ConfigNameStrings.Transport.*;

/**
 * @author David Liu
 */
public class BasicEurekaTransportConfig implements EurekaTransportConfig {

    public static final String CONNECTION_AUTO_TIMEOUT_MS = String.valueOf(30*60*1000);
    public static final String DEFAULT_CODEC = "Avro";

    private Long connectionAutoTimeoutMs = Long.parseLong(
            System.getProperty(connectionAutoTimeoutMsName, CONNECTION_AUTO_TIMEOUT_MS)
    );
    private EurekaTransports.Codec codec = EurekaTransports.Codec.valueOf(
            System.getProperty(codecName, DEFAULT_CODEC)
    );

    public BasicEurekaTransportConfig() {
        this(null, null);
    }

    public BasicEurekaTransportConfig(Long connectionAutoTimeoutMs) {
        this(connectionAutoTimeoutMs, null);
    }

    public BasicEurekaTransportConfig(EurekaTransports.Codec codec) {
        this(null, codec);
    }

    public BasicEurekaTransportConfig(Long connectionAutoTimeoutMs, EurekaTransports.Codec codec) {
        this.connectionAutoTimeoutMs = connectionAutoTimeoutMs == null ? this.connectionAutoTimeoutMs : connectionAutoTimeoutMs;
        this.codec = codec == null ? this.codec : codec;
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
}
