package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;

/**
 * @author Tomasz Bak
 */
public class EurekaClusterDiscoveryConfigBean implements EurekaClusterDiscoveryConfig {

    private final ResolverType clusterResolverType;
    private final ClusterAddress[] clusterAddresses;
    private final String readClusterVipAddress;

    public EurekaClusterDiscoveryConfigBean(ResolverType clusterResolverType, ClusterAddress[] clusterAddresses,
                                            String readClusterVipAddress) {
        this.clusterResolverType = clusterResolverType;
        this.clusterAddresses = clusterAddresses;
        this.readClusterVipAddress = readClusterVipAddress;
    }

    @Override
    public ResolverType getClusterResolverType() {
        return clusterResolverType;
    }

    @Override
    public ClusterAddress[] getClusterAddresses() {
        return clusterAddresses;
    }

    @Override
    public String getReadClusterVipAddress() {
        return readClusterVipAddress;
    }

    public static Builder anEurekaClusterDiscoveryConfig() {
        return new Builder();
    }

    public static class Builder {
        private ResolverType clusterResolverType = ResolverType.Fixed;
        private ClusterAddress[] clusterAddresses = {ClusterAddress.valueOf("localhost", 12102, 12103, 12104)};
        private String readClusterVipAddress = "eureka-read";

        private Builder() {
        }

        public Builder withClusterResolverType(ResolverType clusterResolverType) {
            this.clusterResolverType = clusterResolverType;
            return this;
        }

        public Builder withClusterAddresses(ClusterAddress... clusterAddresses) {
            this.clusterAddresses = clusterAddresses;
            return this;
        }

        public Builder withReadClusterVipAddress(String readClusterVipAddress) {
            this.readClusterVipAddress = readClusterVipAddress;
            return this;
        }

        public Builder but() {
            return anEurekaClusterDiscoveryConfig().withClusterResolverType(clusterResolverType).withClusterAddresses(clusterAddresses).withReadClusterVipAddress(readClusterVipAddress);
        }

        public EurekaClusterDiscoveryConfigBean build() {
            EurekaClusterDiscoveryConfigBean eurekaClusterDiscoveryConfigBean = new EurekaClusterDiscoveryConfigBean(
                    clusterResolverType, clusterAddresses, readClusterVipAddress);
            return eurekaClusterDiscoveryConfigBean;
        }
    }
}
