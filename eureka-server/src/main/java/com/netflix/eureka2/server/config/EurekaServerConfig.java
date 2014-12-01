package com.netflix.eureka2.server.config;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.server.registry.eviction.EvictionStrategyProvider;
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

    @Configuration("eureka.services.registration.port")
    private int registrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;

    @Configuration("eureka.services.replication.port")
    private int replicationPort = EurekaTransports.DEFAULT_REPLICATION_PORT;

    @Configuration("eureka.services.discovery.port")  // all servers support read by default
    protected Integer discoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;

    // registry configs
    @Configuration("eureka.registry.evictionTimeoutMs")
    private long evictionTimeoutMs = EvictionQueueImpl.DEFAULT_EVICTION_TIMEOUT;

    @Configuration("eureka.registry.evictionStrategy.type")
    private String evictionStrategyType = EvictionStrategyProvider.StrategyType.PercentageDrop.name();

    @Configuration("eureka.registry.evictionStrategy.value")
    private String evictionStrategyValue = "20";


    // For property injection
    protected EurekaServerConfig() {
    }

    protected EurekaServerConfig(
            ResolverType resolverType,
            String[] serverList,
            Codec codec,
            String appName,
            String vipAddress,
            DataCenterType dataCenterType,
            Integer shutDownPort,
            Integer webAdminPort,
            Integer registrationPort,
            Integer replicationPort,
            Integer discoveryPort,
            Long evictionTimeoutMs,
            EvictionStrategyProvider.StrategyType evictionStrategyType,
            String evictionStrategyValue
    ) {
        super(resolverType, serverList, codec, appName, vipAddress, dataCenterType, shutDownPort, webAdminPort);

        this.registrationPort = registrationPort == null ? this.registrationPort : registrationPort;
        this.replicationPort = replicationPort == null ? this.replicationPort : replicationPort;
        this.discoveryPort = discoveryPort == null ? this.discoveryPort : discoveryPort;
        this.evictionTimeoutMs = evictionTimeoutMs == null ? this.evictionTimeoutMs : evictionTimeoutMs;
        this.evictionStrategyType = evictionStrategyType == null ? this.evictionStrategyType : evictionStrategyType.name();
        this.evictionStrategyValue = evictionStrategyValue == null ? this.evictionStrategyValue : evictionStrategyValue;
    }

    public int getRegistrationPort() {
        return registrationPort;
    }

    public int getReplicationPort() {
        return replicationPort;
    }

    public int getDiscoveryPort() {
        return discoveryPort;
    }

    public long getEvictionTimeoutMs() {
        return evictionTimeoutMs;
    }

    public EvictionStrategyProvider.StrategyType getEvictionStrategyType() {
        EvictionStrategyProvider.StrategyType type;
        try {
            type = EvictionStrategyProvider.StrategyType.valueOf(evictionStrategyType);
        } catch (Exception e) {
            type = EvictionStrategyProvider.StrategyType.PercentageDrop;
        }

        return type;
    }

    public String getEvictionStrategyValue() {
        return evictionStrategyValue;
    }

    public static DefaultEurekaServerConfigBuilder baseBuilder() {
        return new DefaultEurekaServerConfigBuilder();
    }

    // default builder
    public static class DefaultEurekaServerConfigBuilder
            extends EurekaServerConfigBuilder<EurekaServerConfig, DefaultEurekaServerConfigBuilder> {

        @Override
        public EurekaServerConfig build() {
            return new EurekaServerConfig(
                    resolverType,
                    serverList,
                    codec,
                    appName,
                    vipAddress,
                    dataCenterType,
                    shutDownPort,
                    webAdminPort,
                    registrationPort,
                    replicationPort,
                    discoveryPort,
                    evictionTimeoutMs,
                    evictionStrategyType,
                    evictionStrategyValue
            );
        }
    }

    // builder
    public abstract static class EurekaServerConfigBuilder<C extends EurekaServerConfig, B extends EurekaServerConfigBuilder<C, B>>
            extends EurekaCommonConfigBuilder<C, B> {
        protected Integer registrationPort;
        protected Integer replicationPort;
        protected Integer discoveryPort;
        protected Long evictionTimeoutMs;
        protected EvictionStrategyProvider.StrategyType evictionStrategyType;
        protected String evictionStrategyValue;

        protected EurekaServerConfigBuilder() {}

        public B withRegistrationPort(int writeServerPort) {
            this.registrationPort = writeServerPort;
            return self();
        }

        public B withReplicationPort(int replicationPort) {
            this.replicationPort = replicationPort;
            return self();
        }

        public B withDiscoveryPort(int discoveryPort) {
            this.discoveryPort = discoveryPort;
            return self();
        }

        public B withEvictionTimeout(long evictionTimeoutMs) {
            this.evictionTimeoutMs = evictionTimeoutMs;
            return self();
        }

        public B withEvictionStrategyType(EvictionStrategyProvider.StrategyType strategyType) {
            this.evictionStrategyType = strategyType;
            return self();
        }

        public B withEvictionStrategyValue(String strategyValue) {
            this.evictionStrategyValue = strategyValue;
            return self();
        }
    }
}
