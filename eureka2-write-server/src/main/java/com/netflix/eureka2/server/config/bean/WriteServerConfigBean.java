package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerRegistryConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;

import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerRegistryConfigBean.anEurekaServerRegistryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class WriteServerConfigBean extends EurekaServerConfigBean implements WriteServerConfig {

    private final long replicationReconnectDelayMillis;
    private final boolean bootstrapEnabled;
    private final ResolverType bootstrapResolverType;
    private final ClusterAddress[] bootstrapClusterAddresses;
    private final long bootstrapTimeoutMillis;

    public WriteServerConfigBean(EurekaClusterDiscoveryConfig clusterDiscoveryConfig, EurekaInstanceInfoConfig instanceInfoConfig,
                                 EurekaServerTransportConfig transportConfig, EurekaServerRegistryConfig registryConfig,
                                 long replicationReconnectDelayMillis, boolean bootstrapEnabled, ResolverType bootstrapResolverType,
                                 ClusterAddress[] bootstrapClusterAddresses, long bootstrapTimeoutMillis) {
        super(clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig);
        this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapResolverType = bootstrapResolverType;
        this.bootstrapClusterAddresses = bootstrapClusterAddresses;
        this.bootstrapTimeoutMillis = bootstrapTimeoutMillis;
    }

    @Override
    public long getReplicationReconnectDelayMs() {
        return replicationReconnectDelayMillis;
    }

    @Override
    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    @Override
    public ResolverType getBootstrapResolverType() {
        return bootstrapResolverType;
    }

    @Override
    public ClusterAddress[] getBootstrapClusterAddresses() {
        return bootstrapClusterAddresses;
    }

    @Override
    public long getBootstrapTimeoutMs() {
        return bootstrapTimeoutMillis;
    }

    public static Builder aWriteServerConfig() {
        return new Builder();
    }

    public static class Builder {
        private EurekaClusterDiscoveryConfig clusterDiscoveryConfig = anEurekaClusterDiscoveryConfig().build();
        private EurekaInstanceInfoConfig instanceInfoConfig = anEurekaInstanceInfoConfig().build();
        private EurekaServerTransportConfig transportConfig = anEurekaServerTransportConfig().build();
        private EurekaServerRegistryConfig registryConfig = anEurekaServerRegistryConfig().build();
        private long replicationReconnectDelayMillis = DEFAULT_REPLICATION_RECONNECT_DELAY_MS;
        private boolean bootstrapEnabled = DEFAULT_BOOTSTRAP_ENABLED;
        private ResolverType bootstrapResolverType = ResolverType.Fixed;
        private ClusterAddress[] bootstrapClusterAddresses;
        private long bootstrapTimeoutMillis = DEFAULT_BOOTSTRAP_TIMEOUT_MS;

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

        public Builder withReplicationReconnectDelayMillis(long replicationReconnectDelayMillis) {
            this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
            return this;
        }

        public Builder withBootstrapEnabled(boolean bootstrapEnabled) {
            this.bootstrapEnabled = bootstrapEnabled;
            return this;
        }

        public Builder withBootstrapResolverType(ResolverType bootstrapResolverType) {
            this.bootstrapResolverType = bootstrapResolverType;
            return this;
        }

        public Builder withBootstrapClusterAddresses(ClusterAddress... bootstrapClusterAddresses) {
            this.bootstrapClusterAddresses = bootstrapClusterAddresses;
            return this;
        }

        public Builder withBootstrapTimeoutMillis(long bootstrapTimeoutMillis) {
            this.bootstrapTimeoutMillis = bootstrapTimeoutMillis;
            return this;
        }

        public Builder but() {
            return aWriteServerConfig()
                    .withClusterDiscoveryConfig(clusterDiscoveryConfig)
                    .withInstanceInfoConfig(instanceInfoConfig)
                    .withTransportConfig(transportConfig)
                    .withRegistryConfig(registryConfig)
                    .withReplicationReconnectDelayMillis(replicationReconnectDelayMillis)
                    .withBootstrapEnabled(bootstrapEnabled)
                    .withBootstrapResolverType(bootstrapResolverType)
                    .withBootstrapClusterAddresses(bootstrapClusterAddresses)
                    .withBootstrapTimeoutMillis(bootstrapTimeoutMillis);
        }

        public WriteServerConfigBean build() {
            return new WriteServerConfigBean(
                    clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig,
                    replicationReconnectDelayMillis, bootstrapEnabled, bootstrapResolverType,
                    bootstrapClusterAddresses, bootstrapTimeoutMillis);
        }
    }
}
