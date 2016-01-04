package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaServerTransportConfigBean implements EurekaServerTransportConfig {

    private final int httpPort;
    private final int shutDownPort;
    private final int webAdminPort;
    private final int registrationPort;
    private final long heartbeatIntervalMs;
    private final long connectionAutoTimeoutMs;

    public EurekaServerTransportConfigBean(int httpPort, int shutDownPort, int webAdminPort,
                                           int registrationPort, long heartbeatIntervalMs,
                                           long connectionAutoTimeoutMs) {
        this.httpPort = httpPort;
        this.shutDownPort = shutDownPort;
        this.webAdminPort = webAdminPort;
        this.registrationPort = registrationPort;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.connectionAutoTimeoutMs = connectionAutoTimeoutMs;
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
    public int getServerPort() {
        return registrationPort;
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    @Override
    public long getConnectionAutoTimeoutMs() {
        return connectionAutoTimeoutMs;
    }

    public static Builder anEurekaServerTransportConfig() {
        return new Builder();
    }

    public static Builder anEurekaServerTransportConfig(EurekaServerTransportConfig original) {
        return new Builder()
                .withHttpPort(original.getHttpPort())
                .withShutDownPort(original.getShutDownPort())
                .withWebAdminPort(original.getWebAdminPort())
                .withServerPort(original.getServerPort())
                .withHeartbeatIntervalMs(original.getHeartbeatIntervalMs())
                .withConnectionAutoTimeoutMs(original.getConnectionAutoTimeoutMs());
    }

    public static class Builder {
        private int httpPort = DEFAULT_HTTP_PORT;
        private int shutDownPort = DEFAULT_SHUTDOWN_PORT;
        private int webAdminPort = DEFAULT_WEB_ADMIN_PORT;
        private int serverPort = DEFAULT_SERVER_PORT;
        private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;
        private long connectionAutoTimeoutMs = DEFAULT_CONNECTION_AUTO_TIMEOUT_MS;

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

        public Builder withServerPort(int serverPort) {
            this.serverPort = serverPort;
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

        public Builder but() {
            return anEurekaServerTransportConfig()
                    .withHttpPort(httpPort)
                    .withShutDownPort(shutDownPort)
                    .withWebAdminPort(webAdminPort)
                    .withServerPort(serverPort)
                    .withHeartbeatIntervalMs(heartbeatIntervalMs)
                    .withConnectionAutoTimeoutMs(connectionAutoTimeoutMs);
        }

        public EurekaServerTransportConfigBean build() {
            EurekaServerTransportConfigBean eurekaServerTransportConfigBean = new EurekaServerTransportConfigBean(httpPort, shutDownPort, webAdminPort, serverPort, heartbeatIntervalMs, connectionAutoTimeoutMs);
            return eurekaServerTransportConfigBean;
        }
    }
}
