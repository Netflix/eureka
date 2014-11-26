package com.netflix.eureka2.config;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Configuration;

/**
 * @author David Liu
 */
public class BridgeServerConfig extends EurekaServerConfig {

    @Configuration("services.bridge.refreshRateSec")
    private int refreshRateSec = 30;

    public BridgeServerConfig() {
    }

    public BridgeServerConfig(LocalDataCenterInfo.DataCenterType dataCenterType, String resolverType,
                              int writeServerPort, int replicationPort, int readServerPort, Codec codec, int shutDownPort,
                              String appName, String vipAddress, String writeClusterDomainName,
                              String[] writeClusterServers, int refreshRateSec, int webAdminPort,
                              long registryEvictionTimeout, String evictionStrategyType, String evictionStrategyValue) {
        super(dataCenterType, resolverType, writeServerPort, replicationPort, readServerPort, codec, shutDownPort,
                appName, vipAddress, writeClusterDomainName, writeClusterServers, webAdminPort,
                registryEvictionTimeout, evictionStrategyType, evictionStrategyValue);
        this.refreshRateSec = refreshRateSec;
    }

    public int getRefreshRateSec() {
        return refreshRateSec;
    }

    public static class BridgeServerConfigBuilder extends EurekaServerConfigBuilder<BridgeServerConfig, BridgeServerConfigBuilder> {
        private int refreshRateSec;

        public BridgeServerConfigBuilder withRefreshRateSec(int refreshRateSec) {
            this.refreshRateSec = refreshRateSec;
            return this;
        }

        @Override
        public BridgeServerConfig build() {
            return new BridgeServerConfig(dataCenterType, resolverType, registrationPort, replicationPort,
                    discoveryPort, codec, shutDownPort, appName, vipAddress, writeClusterDomainName, writeClusterServers,
                    refreshRateSec, webAdminPort, evictionTimeout, evictionStrategyType.name(), evictionStrategyValue);
        }
    }

}
