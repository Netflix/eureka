package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerRegistryConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerRegistryConfigBean.anEurekaServerRegistryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaServerConfigBean implements EurekaServerConfig {

    private final EurekaClusterDiscoveryConfig clusterDiscoveryConfig;
    private final EurekaInstanceInfoConfig instanceInfoConfig;
    private final EurekaServerTransportConfig transportConfig;
    private final EurekaServerRegistryConfig registryConfig;

    public EurekaServerConfigBean(EurekaClusterDiscoveryConfig clusterDiscoveryConfig, EurekaInstanceInfoConfig instanceInfoConfig,
                                  EurekaServerTransportConfig transportConfig, EurekaServerRegistryConfig registryConfig) {
        this.clusterDiscoveryConfig = clusterDiscoveryConfig;
        this.instanceInfoConfig = instanceInfoConfig;
        this.transportConfig = transportConfig;
        this.registryConfig = registryConfig;
    }

    @Override
    public EurekaClusterDiscoveryConfig getEurekaClusterDiscovery() {
        return clusterDiscoveryConfig;
    }

    @Override
    public EurekaInstanceInfoConfig getEurekaInstance() {
        return instanceInfoConfig;
    }

    @Override
    public EurekaServerTransportConfig getEurekaTransport() {
        return transportConfig;
    }

    @Override
    public EurekaServerRegistryConfig getEurekaRegistry() {
        return registryConfig;
    }

    public static Builder anEurekaServerConfig() {
        return new Builder();
    }

    public static class Builder {
        private EurekaClusterDiscoveryConfig clusterDiscoveryConfig = anEurekaClusterDiscoveryConfig().build();
        private EurekaInstanceInfoConfig instanceInfoConfig = anEurekaInstanceInfoConfig().build();
        private EurekaServerTransportConfig transportConfig = anEurekaServerTransportConfig().build();
        private EurekaServerRegistryConfig registryConfig = anEurekaServerRegistryConfig().build();

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

        public Builder but() {
            return anEurekaServerConfig().withClusterDiscoveryConfig(clusterDiscoveryConfig).withInstanceInfoConfig(instanceInfoConfig).withTransportConfig(transportConfig).withRegistryConfig(registryConfig);
        }

        public EurekaServerConfigBean build() {
            EurekaServerConfigBean eurekaServerConfigBean = new EurekaServerConfigBean(clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig);
            return eurekaServerConfigBean;
        }
    }
}
