package com.netflix.eureka2.config;

import com.netflix.eureka2.transport.EurekaTransports;

/**
 * @author David Liu
 */
public interface EurekaTransportConfig {

    public long getConnectionAutoTimeoutMs();

    public EurekaTransports.Codec getCodec();
}
