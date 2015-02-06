package com.netflix.eureka2.server.config;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Configuration;

/**
 * This class contains essential configuration data that are required during Eureka read/write server
 * bootstrapping. Multiple sources of this data are supported, like command line arguments,
 * property configuration file and archaius.
 * @author Tomasz Bak
 */
public class EurekaServerConfig extends EurekaCommonConfig {

    @Configuration("eureka.services.discovery.port")  // all servers support read by default
    protected Integer discoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;


    // For property injection
    protected EurekaServerConfig() {
    }

    protected EurekaServerConfig(
            ResolverType resolverType,
            String[] serverList,
            String appName,
            String vipAddress,
            DataCenterType dataCenterType,
            Integer shutDownPort,
            Integer webAdminPort,
            Integer discoveryPort,
            Long connectionAutoTimeoutMs,
            Codec codec,
            Long evictionTimeoutMs,
            StrategyType evictionStrategyType,
            String evictionStrategyValue
    ) {
        super(resolverType, serverList, appName, vipAddress, dataCenterType, shutDownPort, webAdminPort,
                connectionAutoTimeoutMs, codec, evictionTimeoutMs, evictionStrategyType, evictionStrategyValue);
        this.discoveryPort = discoveryPort == null ? this.discoveryPort : discoveryPort;
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
                    dataCenterType,
                    shutDownPort,
                    webAdminPort,
                    discoveryPort,
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
        protected Integer discoveryPort;

        protected AbstractEurekaServerConfigBuilder() {
        }

        public B withDiscoveryPort(int discoveryPort) {
            this.discoveryPort = discoveryPort;
            return self();
        }
    }
}
