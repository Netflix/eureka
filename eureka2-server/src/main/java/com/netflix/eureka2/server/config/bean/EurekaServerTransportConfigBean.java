package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaServerTransportConfigBean implements EurekaServerTransportConfig {

    private final int httpPort;
    private final int shutDownPort;
    private final int webAdminPort;
    private final int interestPort;
    private final int registrationPort;
    private final int replicationPort;
    private final long heartbeatIntervalMs;
    private final long connectionAutoTimeoutMs;
    private final CodecType codec;

    public EurekaServerTransportConfigBean(int httpPort, int shutDownPort, int webAdminPort, int interestPort,
                                           int registrationPort, int replicationPort, long heartbeatIntervalMs,
                                           long connectionAutoTimeoutMs, CodecType codec) {
        this.httpPort = httpPort;
        this.shutDownPort = shutDownPort;
        this.webAdminPort = webAdminPort;
        this.interestPort = interestPort;
        this.registrationPort = registrationPort;
        this.replicationPort = replicationPort;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
        this.codec = codec;
    }

    @Override
    public int getHttpPort() {
        return httpPort;
    }

    @Override
    public int getShutDownPort() {
        return shutDownPort;
    }

    @Override
    public int getWebAdminPort() {
        return webAdminPort;
    }

    @Override
    public int getInterestPort() {
        return interestPort;
    }

    @Override
    public int getRegistrationPort() {
        return registrationPort;
    }

    @Override
    public int getReplicationPort() {
        return replicationPort;
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

    public static Builder anEurekaServerTransportConfig() {
        return new Builder();
    }

    public static Builder anEurekaServerTransportConfig(EurekaServerTransportConfig original) {
        return new Builder()
                .withHttpPort(original.getHttpPort())
                .withShutDownPort(original.getShutDownPort())
                .withWebAdminPort(original.getWebAdminPort())
                .withInterestPort(original.getInterestPort())
                .withRegistrationPort(original.getRegistrationPort())
                .withReplicationPort(original.getReplicationPort())
                .withHeartbeatIntervalMs(original.getHeartbeatIntervalMs())
                .withConnectionAutoTimeoutMs(original.getConnectionAutoTimeoutMs())
                .withCodec(original.getCodec());
    }

    public static class Builder {
        private int httpPort = DEFAULT_HTTP_PORT;
        private int shutDownPort = DEFAULT_SHUTDOWN_PORT;
        private int webAdminPort = DEFAULT_WEB_ADMIN_PORT;
        private int interestPort = DEFAULT_INTEREST_PORT;
        private int registrationPort = DEFAULT_REGISTRATION_PORT;
        private int replicationPort = DEFAULT_REPLICATION_PORT;
        private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;
        private long connectionAutoTimeoutMs = DEFAULT_CONNECTION_AUTO_TIMEOUT_MS;
        private CodecType codec = CodecType.Avro;

        private Builder() {
        }

        public Builder withHttpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder withShutDownPort(int shutDownPort) {
            this.shutDownPort = shutDownPort;
            return this;
        }

        public Builder withWebAdminPort(int webAdminPort) {
            this.webAdminPort = webAdminPort;
            return this;
        }

        public Builder withInterestPort(int interestPort) {
            this.interestPort = interestPort;
            return this;
        }

        public Builder withRegistrationPort(int registrationPort) {
            this.registrationPort = registrationPort;
            return this;
        }

        public Builder withReplicationPort(int replicationPort) {
            this.replicationPort = replicationPort;
            return this;
        }

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

        public Builder but() {
            return anEurekaServerTransportConfig()
                    .withHttpPort(httpPort)
                    .withShutDownPort(shutDownPort)
                    .withWebAdminPort(webAdminPort)
                    .withInterestPort(interestPort)
                    .withRegistrationPort(registrationPort)
                    .withReplicationPort(replicationPort)
                    .withHeartbeatIntervalMs(heartbeatIntervalMs)
                    .withConnectionAutoTimeoutMs(connectionAutoTimeoutMs)
                    .withCodec(codec);
        }

        public EurekaServerTransportConfigBean build() {
            EurekaServerTransportConfigBean eurekaServerTransportConfigBean = new EurekaServerTransportConfigBean(httpPort, shutDownPort, webAdminPort, interestPort, registrationPort, replicationPort, heartbeatIntervalMs, connectionAutoTimeoutMs, codec);
            return eurekaServerTransportConfigBean;
        }
    }
}
