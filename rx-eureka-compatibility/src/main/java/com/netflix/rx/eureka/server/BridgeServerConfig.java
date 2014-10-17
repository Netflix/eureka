package com.netflix.rx.eureka.server;

import com.netflix.governator.annotations.Configuration;
import com.netflix.rx.eureka.registry.datacenter.LocalDataCenterInfo;

/**
 * @author David Liu
 */
public class BridgeServerConfig extends EurekaBootstrapConfig {

    @Configuration("bridgeServer.refreshRateSec")
    private int refreshRateSec = 30;

    public BridgeServerConfig() {
    }

    public BridgeServerConfig(LocalDataCenterInfo.DataCenterType dataCenterType, String resolverType,
                             int writeServerPort, int replicationPort, int readServerPort, int shutDownPort,
                             String appName, String vipAddress, String writeClusterDomainName,
                             String[] writeClusterServers, int refreshRateSec) {
        super(dataCenterType, resolverType, writeServerPort, replicationPort, readServerPort, shutDownPort,
                appName, vipAddress, writeClusterDomainName, writeClusterServers);
        this.refreshRateSec = refreshRateSec;
    }

    public int getRefreshRateSec() {
        return refreshRateSec;
    }

    public static class BridgeServerConfigBuilder extends EurekaBootstrapConfigBuilder<BridgeServerConfig, BridgeServerConfigBuilder> {
        private int refreshRateSec;

        public BridgeServerConfigBuilder withRefreshRateSec(int refreshRateSec) {
            this.refreshRateSec = refreshRateSec;
            return this;
        }

        @Override
        public BridgeServerConfig build() {
            return new BridgeServerConfig(dataCenterType, resolverType, writeServerPort, replicationPort,
                    readServerPort, shutDownPort, appName, vipAddress,  writeClusterDomainName, writeClusterServers,
                    refreshRateSec);
        }
    }

}
