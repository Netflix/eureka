package com.netflix.eureka2.config;

import com.netflix.eureka2.transport.EurekaTransports;

/**
 * @author David Liu
 */
public interface EurekaTransportConfig {

    long getHeartbeatIntervalMs();

    long getConnectionAutoTimeoutMs();

    EurekaTransports.Codec getCodec();
}
