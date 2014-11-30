package com.netflix.eureka2.config;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.registry.eviction.EvictionStrategyProvider;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.governator.annotations.Configuration;

/**
 * @author David Liu
 */
public class BridgeServerConfig extends EurekaServerConfig {

    @Configuration("eureka.services.bridge.refreshRateSec")
    private int refreshRateSec = 30;


    // For property injection
    protected BridgeServerConfig() {
    }

    protected BridgeServerConfig(
            // common server configs
            ResolverType resolverType,
            String[] serverList,
            EurekaTransports.Codec codec,
            Integer registrationPort,
            Integer replicationPort,
            Integer discoveryPort,
            Integer shutDownPort,
            String appName,
            String vipAddress,
            LocalDataCenterInfo.DataCenterType dataCenterType,
            Integer webAdminPort,
            Long evictionTimeoutMs,
            EvictionStrategyProvider.StrategyType evictionStrategyType,
            String evictionStrategyValue,
            // bridge server configs
            Integer refreshRateSec
    ) {
        super(
                resolverType,
                serverList,
                codec,
                registrationPort,
                replicationPort,
                discoveryPort,
                shutDownPort,
                appName,
                vipAddress,
                dataCenterType,
                webAdminPort,
                evictionTimeoutMs,
                evictionStrategyType,
                evictionStrategyValue
        );

        this.refreshRateSec = refreshRateSec == null ? this.refreshRateSec : refreshRateSec;
    }

    public int getRefreshRateSec() {
        return refreshRateSec;
    }

    public static BridgeServerConfigBuilder newBuilder() {
        return new BridgeServerConfigBuilder();
    }


    // builder
    public static class BridgeServerConfigBuilder
            extends EurekaServerConfig.EurekaServerConfigBuilder<BridgeServerConfig, BridgeServerConfigBuilder> {

        protected Integer refreshRateSec;

        protected BridgeServerConfigBuilder() {}

        public BridgeServerConfigBuilder withRefreshRateSec(int refreshRateSec) {
            this.refreshRateSec = refreshRateSec;
            return self();
        }

        public BridgeServerConfig build() {
            return new BridgeServerConfig(
                    resolverType,
                    serverList,
                    codec,
                    registrationPort,
                    replicationPort,
                    discoveryPort,
                    shutDownPort,
                    appName,
                    vipAddress,
                    dataCenterType,
                    webAdminPort,
                    evictionTimeoutMs,
                    evictionStrategyType,
                    evictionStrategyValue,
                    // bridge server configs
                    refreshRateSec
            );
        }
    }
}