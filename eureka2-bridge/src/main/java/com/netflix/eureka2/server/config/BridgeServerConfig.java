package com.netflix.eureka2.server.config;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.governator.annotations.Configuration;

/**
 * @author David Liu
 */
public class BridgeServerConfig extends WriteServerConfig {

    @Configuration("eureka.services.bridge.refreshRateSec")
    private int refreshRateSec = 30;


    // For property injection
    protected BridgeServerConfig() {
    }

    protected BridgeServerConfig(
            // common server configs
            ResolverType resolverType,
            String[] serverList,
            String appName,
            String vipAddress,
            LocalDataCenterInfo.DataCenterType dataCenterType,
            Integer shutDownPort,
            Integer webAdminPort,
            Integer discoveryPort,
            Long connectionAutoTimeoutMs,
            EurekaTransports.Codec codec,
            Long evictionTimeoutMs,
            StrategyType evictionStrategyType,
            String evictionStrategyValue,
            Integer registrationPort,
            Integer replicationPort,
            Long replicationReconnectDelayMillis,
            // bridge server configs
            Integer refreshRateSec
    ) {
        super(
                resolverType,
                serverList,
                appName,
                vipAddress,
                dataCenterType,
                shutDownPort,
                webAdminPort,
                discoveryPort,
                connectionAutoTimeoutMs,
                codec,
                evictionTimeoutMs,
                evictionStrategyType,
                evictionStrategyValue,
                registrationPort,
                replicationPort,
                replicationReconnectDelayMillis
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
            extends WriteServerConfig.AbstractWriteServerConfigBuilder<BridgeServerConfig, BridgeServerConfigBuilder> {

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
                    appName,
                    vipAddress,
                    dataCenterType,
                    shutDownPort,
                    webAdminPort,
                    discoveryPort,
                    connectionAutoTimeoutMs,
                    codec,
                    evictionTimeoutMs,
                    evictionStrategyType,
                    evictionStrategyValue,
                    registrationPort,
                    replicationPort,
                    replicationReconnectDelayMillis,
                    // bridge server configs
                    refreshRateSec
            );
        }
    }
}