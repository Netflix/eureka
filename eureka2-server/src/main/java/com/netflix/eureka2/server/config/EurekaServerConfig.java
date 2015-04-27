package com.netflix.eureka2.server.config;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolvers.ResolverType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.governator.annotations.Configuration;

/**
 * This class contains essential configuration data that are required during Eureka read/write server
 * bootstrapping. Multiple sources of this data are supported, like command line arguments,
 * property configuration file and archaius.
 * @author Tomasz Bak
 */
public class EurekaServerConfig extends EurekaCommonConfig {

    @Configuration("eureka.services.discovery.port")  // all servers support read by default
    protected int discoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;


    // For property injection
    protected EurekaServerConfig() {
    }

    protected EurekaServerConfig(
            ResolverType resolverType,
            String[] serverList,
            String appName,
            String vipAddress,
            String readClusterVipAddress,
            DataCenterType dataCenterType,
            int dataCenterResolveIntervalSec,
            int httpPort,
            int shutDownPort,
            int webAdminPort,
            int discoveryPort,
            long heartbeatIntervalMs,
            long connectionAutoTimeoutMs,
            CodecType codec,
            long evictionTimeoutMs,
            StrategyType evictionStrategyType,
            String evictionStrategyValue
    ) {
        super(resolverType, serverList, appName, vipAddress, readClusterVipAddress,
                dataCenterType, dataCenterResolveIntervalSec,
                httpPort, shutDownPort, webAdminPort, heartbeatIntervalMs, connectionAutoTimeoutMs, codec,
                evictionTimeoutMs, evictionStrategyType, evictionStrategyValue);
        this.discoveryPort = discoveryPort;
    }

    public int getDiscoveryPort() {
        return discoveryPort;
    }

    public static EurekaServerConfigBuilder baseBuilder() {
        return new EurekaServerConfigBuilder();
    }

    // default builder
    public static class EurekaServerConfigBuilder
            extends AbstractEurekaServerConfigBuilder<EurekaServerConfig, EurekaServerConfigBuilder> {

        @Override
        public EurekaServerConfig build() {
            return new EurekaServerConfig(
                    resolverType,
                    serverList,
                    appName,
                    vipAddress,
                    readClusterVipAddress,
                    dataCenterType,
                    dataCenterResolveIntervalSec,
                    httpPort,
                    shutDownPort,
                    webAdminPort,
                    discoveryPort,
                    heartbeatIntervalMs,
                    connectionAutoTimeoutMs,
                    codec,
                    evictionTimeoutMs,
                    evictionStrategyType,
                    evictionStrategyValue
            );
        }
    }

    // builder
    public abstract static class AbstractEurekaServerConfigBuilder<C extends EurekaServerConfig, B extends AbstractEurekaServerConfigBuilder<C, B>>
            extends EurekaCommonConfigBuilder<C, B> {
        protected int discoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;

        protected AbstractEurekaServerConfigBuilder() {
        }

        public B withDiscoveryPort(int discoveryPort) {
            this.discoveryPort = discoveryPort;
            return self();
        }
    }
}
