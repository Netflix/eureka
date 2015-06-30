package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.config.EurekaTransportConfig;

/**
 * @author Tomasz Bak
 */
public interface EurekaServerTransportConfig extends EurekaTransportConfig {

    int DEFAULT_HTTP_PORT = 8080;
    int DEFAULT_SHUTDOWN_PORT = 7700;
    int DEFAULT_WEB_ADMIN_PORT = 8077;
    int DEFAULT_INTEREST_PORT = 12103;
    int DEFAULT_REGISTRATION_PORT = 12102;
    int DEFAULT_REPLICATION_PORT = 12104;
    long DEFAULT_HEARTBEAT_INTERVAL_MS = 30000;
    long DEFAULT_CONNECTION_AUTO_TIMEOUT_MS = 30 * 60 * 1000;

    @DefaultValue("" + DEFAULT_HTTP_PORT)
    int getHttpPort();

    @DefaultValue("" + DEFAULT_SHUTDOWN_PORT)
    int getShutDownPort();

    @DefaultValue("" + DEFAULT_WEB_ADMIN_PORT)
    int getWebAdminPort();

    @DefaultValue("" + DEFAULT_INTEREST_PORT)
    int getInterestPort();

    @DefaultValue("" + DEFAULT_REGISTRATION_PORT)
    int getRegistrationPort();

    @DefaultValue("" + DEFAULT_REPLICATION_PORT)
    int getReplicationPort();

    @Override
    @DefaultValue("" + DEFAULT_HEARTBEAT_INTERVAL_MS)
    long getHeartbeatIntervalMs();

    @Override
    @DefaultValue("" + DEFAULT_CONNECTION_AUTO_TIMEOUT_MS)
    long getConnectionAutoTimeoutMs();

    @Override
    @DefaultValue("Avro")
    CodecType getCodec();
}
