package com.netflix.eureka2.config;

import com.netflix.eureka2.transport.EurekaTransports.Codec;

/**
 * @author David Liu
 */
public interface EurekaTransportConfig {

    long getHeartbeatIntervalMs();

    long getConnectionAutoTimeoutMs();

    Codec getCodec();
}
