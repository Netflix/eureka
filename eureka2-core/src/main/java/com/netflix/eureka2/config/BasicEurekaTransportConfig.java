package com.netflix.eureka2.config;

import com.netflix.eureka2.transport.EurekaTransports;

import static com.netflix.eureka2.config.ConfigNameStrings.Transport.*;

/**
 * @author David Liu
 */
public class BasicEurekaTransportConfig implements EurekaTransportConfig {

    private static final String CONNECTION_AUTO_TIMEOUT_MS = String.valueOf(30*60*1000);
    private static final String DEFAULT_CODEC = "Avro";

    private String connectionAutoTimeoutMs = System.getProperty(connectionAutoTimeoutMsName, CONNECTION_AUTO_TIMEOUT_MS);
    private String codec = System.getProperty(codecName, DEFAULT_CODEC);

    public BasicEurekaTransportConfig() {
        this(null, null);
    }

    public BasicEurekaTransportConfig(Long connectionAutoTimeoutMs, EurekaTransports.Codec codec) {
        this.connectionAutoTimeoutMs = connectionAutoTimeoutMs == null ? this.connectionAutoTimeoutMs : connectionAutoTimeoutMs.toString();
        this.codec = codec == null ? this.codec : codec.name();
    }
    @Override
    public long getConnectionAutoTimeoutMs() {
        return Long.parseLong(connectionAutoTimeoutMs);
    }

    @Override
    public EurekaTransports.Codec getCodec() {
        return EurekaTransports.Codec.valueOf(codec);
    }

    @Override
    public String toString() {
        return "BasicEurekaTransportConfig{" +
                "connectionAutoTimeoutMs='" + connectionAutoTimeoutMs + '\'' +
                ", codec='" + codec + '\'' +
                '}';
    }
}
