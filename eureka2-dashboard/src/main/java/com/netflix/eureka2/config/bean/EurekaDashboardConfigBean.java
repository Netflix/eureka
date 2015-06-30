package com.netflix.eureka2.config.bean;

import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerRegistryConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.config.bean.EurekaServerConfigBean;

import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerRegistryConfigBean.anEurekaServerRegistryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaDashboardConfigBean extends EurekaServerConfigBean implements EurekaDashboardConfig {

    private final int dashboardPort;
    private final int webSocketPort;

    public EurekaDashboardConfigBean(EurekaClusterDiscoveryConfig clusterDiscoveryConfig,
                                     EurekaInstanceInfoConfig instanceInfoConfig,
                                     EurekaServerTransportConfig transportConfig,
                                     EurekaServerRegistryConfig registryConfig,
                                     int dashboardPort,
                                     int webSocketPort) {
        super(clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig);
        this.dashboardPort = dashboardPort;
        this.webSocketPort = webSocketPort;
    }

    @Override
    public int getDashboardPort() {
        return dashboardPort;
    }

    @Override
    public int getWebSocketPort() {
        return webSocketPort;
    }

    public static Builder aDashboardConfig() {
        return new Builder();
    }

    public static class Builder {

        private EurekaClusterDiscoveryConfig clusterDiscoveryConfig = anEurekaClusterDiscoveryConfig().build();
        private EurekaInstanceInfoConfig instanceInfoConfig = anEurekaInstanceInfoConfig().build();
        private EurekaServerTransportConfig transportConfig = anEurekaServerTransportConfig().build();
        private EurekaServerRegistryConfig registryConfig = anEurekaServerRegistryConfig().build();
        private int dashboardPort = DEFAULT_DASHBOARD_PORT;
        private int webSocketPort = DEFAULT_WEB_SOCKET_PORT;

        private Builder() {
        }

        public Builder withClusterDiscoveryConfig(EurekaClusterDiscoveryConfig clusterDiscoveryConfig) {
            this.clusterDiscoveryConfig = clusterDiscoveryConfig;
            return this;
        }

        public Builder withInstanceInfoConfig(EurekaInstanceInfoConfig instanceInfoConfig) {
            this.instanceInfoConfig = instanceInfoConfig;
            return this;
        }

        public Builder withTransportConfig(EurekaServerTransportConfig transportConfig) {
            this.transportConfig = transportConfig;
            return this;
        }

        public Builder withRegistryConfig(EurekaServerRegistryConfig registryConfig) {
            this.registryConfig = registryConfig;
            return this;
        }

        public Builder withDashboardPort(int dashboardPort) {
            this.dashboardPort = dashboardPort;
            return this;
        }

        public Builder withWebSocketPort(int webSocketPort) {
            this.webSocketPort = webSocketPort;
            return this;
        }

        public Builder but() {
            return aDashboardConfig()
                    .withClusterDiscoveryConfig(clusterDiscoveryConfig)
                    .withInstanceInfoConfig(instanceInfoConfig)
                    .withTransportConfig(transportConfig)
                    .withRegistryConfig(registryConfig)
                    .withDashboardPort(dashboardPort)
                    .withWebSocketPort(webSocketPort);
        }

        public EurekaDashboardConfigBean build() {
            EurekaDashboardConfigBean eurekaServerConfigBean = new EurekaDashboardConfigBean(
                    clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig,
                    dashboardPort, webSocketPort);
            return eurekaServerConfigBean;
        }
    }
}
