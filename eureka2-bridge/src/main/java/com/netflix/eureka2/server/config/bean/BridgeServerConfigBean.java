package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.BootstrapConfig;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerRegistryConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerRegistryConfigBean.anEurekaServerRegistryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class BridgeServerConfigBean extends WriteServerConfigBean implements BridgeServerConfig {

    private final int refreshRateSec;

    public BridgeServerConfigBean(EurekaClusterDiscoveryConfig clusterDiscoveryConfig, EurekaInstanceInfoConfig instanceInfoConfig,
                                  EurekaServerTransportConfig transportConfig, EurekaServerRegistryConfig registryConfig,
                                  BootstrapConfig bootstrapConfig, long replicationReconnectDelayMillis, int refreshRateSec) {
        super(clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig,
                bootstrapConfig, replicationReconnectDelayMillis);
        this.refreshRateSec = refreshRateSec;
    }

    @Override
    public int getRefreshRateSec() {
        return refreshRateSec;
    }

    public static Builder aBridgeServerConfig() {
        return new Builder();
    }

    public static class Builder {
        private EurekaClusterDiscoveryConfig clusterDiscoveryConfig = anEurekaClusterDiscoveryConfig().build();
        private EurekaInstanceInfoConfig instanceInfoConfig = anEurekaInstanceInfoConfig().build();
        private EurekaServerTransportConfig transportConfig = anEurekaServerTransportConfig().build();
        private EurekaServerRegistryConfig registryConfig = anEurekaServerRegistryConfig().build();
        private BootstrapConfig bootstrapConfig = BootstrapConfigBean.aBootstrapConfig().build();
        private long replicationReconnectDelayMillis = DEFAULT_REPLICATION_RECONNECT_DELAY_MS;
        private int refreshRateSec;

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

        public Builder withBootstrapConfig(BootstrapConfig bootstrapConfig) {
            this.bootstrapConfig = bootstrapConfig;
            return this;
        }

        public Builder withReplicationReconnectDelayMillis(long replicationReconnectDelayMillis) {
            this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
            return this;
        }

        private Builder withRefreshRateSec(int refreshRateSec) {
            this.refreshRateSec = refreshRateSec;
            return this;
        }

        public Builder but() {
            return aBridgeServerConfig()
                    .withClusterDiscoveryConfig(clusterDiscoveryConfig)
                    .withInstanceInfoConfig(instanceInfoConfig)
                    .withTransportConfig(transportConfig)
                    .withRegistryConfig(registryConfig)
                    .withReplicationReconnectDelayMillis(replicationReconnectDelayMillis)
                    .withBootstrapConfig(bootstrapConfig)
                    .withRefreshRateSec(refreshRateSec);
        }

        public BridgeServerConfig build() {
            return new BridgeServerConfigBean(
                    clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig,
                    bootstrapConfig, replicationReconnectDelayMillis, refreshRateSec);
        }
    }
}
