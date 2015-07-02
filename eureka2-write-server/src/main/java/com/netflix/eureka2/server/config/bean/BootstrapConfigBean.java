package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.BootstrapConfig;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;

/**
 * @author Tomasz Bak
 */
public class BootstrapConfigBean implements BootstrapConfig {

    private final boolean bootstrapEnabled;
    private final ResolverType bootstrapResolverType;
    private final ClusterAddress[] bootstrapClusterAddresses;
    private final long bootstrapTimeoutMillis;

    public BootstrapConfigBean(boolean bootstrapEnabled, ResolverType bootstrapResolverType,
                               ClusterAddress[] bootstrapClusterAddresses, long bootstrapTimeoutMillis) {
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapResolverType = bootstrapResolverType;
        this.bootstrapClusterAddresses = bootstrapClusterAddresses;
        this.bootstrapTimeoutMillis = bootstrapTimeoutMillis;
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

    public static Builder aBootstrapConfig() {
        return new Builder();
    }

    public static class Builder {
        private boolean bootstrapEnabled = DEFAULT_BOOTSTRAP_ENABLED;
        private ResolverType bootstrapResolverType = ResolverType.Fixed;
        private ClusterAddress[] bootstrapClusterAddresses;
        private long bootstrapTimeoutMillis = DEFAULT_BOOTSTRAP_TIMEOUT_MS;

        private Builder() {
        }

        public Builder withBootstrapEnabled(boolean bootstrapEnabled) {
            this.bootstrapEnabled = bootstrapEnabled;
            return this;
        }

        public Builder withBootstrapResolverType(ResolverType bootstrapResolverType) {
            this.bootstrapResolverType = bootstrapResolverType;
            return this;
        }

        public Builder withBootstrapClusterAddresses(ClusterAddress[] bootstrapClusterAddresses) {
            this.bootstrapClusterAddresses = bootstrapClusterAddresses;
            return this;
        }

        public Builder withBootstrapTimeoutMillis(long bootstrapTimeoutMillis) {
            this.bootstrapTimeoutMillis = bootstrapTimeoutMillis;
            return this;
        }

        public Builder but() {
            return aBootstrapConfig().withBootstrapEnabled(bootstrapEnabled).withBootstrapResolverType(bootstrapResolverType).withBootstrapClusterAddresses(bootstrapClusterAddresses).withBootstrapTimeoutMillis(bootstrapTimeoutMillis);
        }

        public BootstrapConfigBean build() {
            BootstrapConfigBean bootstrapConfigBean = new BootstrapConfigBean(bootstrapEnabled, bootstrapResolverType, bootstrapClusterAddresses, bootstrapTimeoutMillis);
            return bootstrapConfigBean;
        }
    }
}
