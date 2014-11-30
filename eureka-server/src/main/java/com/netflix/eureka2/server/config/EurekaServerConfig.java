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

    @Configuration("eureka.services.shutdown.port")
    protected int shutDownPort = 7700;

    @Configuration("netflix.platform.admin.resources.port")
    protected int webAdminPort = 8077;

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
            Integer registrationPort,
            Integer replicationPort,
            Integer discoveryPort,
            Integer shutDownPort,
            String appName,
            String vipAddress,
            DataCenterType dataCenterType,
            Integer webAdminPort,
            Long evictionTimeoutMs,
            EvictionStrategyProvider.StrategyType evictionStrategyType,
            String evictionStrategyValue
    ) {
        this.resolverType = resolverType == null ? this.resolverType : resolverType.name();
        this.serverList = serverList == null ? this.serverList : serverList;
        this.codec = codec == null ? this.codec : codec.name();
        this.registrationPort = registrationPort == null ? this.registrationPort : registrationPort;
        this.replicationPort = replicationPort == null ? this.replicationPort : replicationPort;
        this.discoveryPort = discoveryPort == null ? this.discoveryPort : discoveryPort;
        this.shutDownPort = shutDownPort == null ? this.shutDownPort : shutDownPort;
        this.appName = appName == null ? this.appName : appName;
        this.vipAddress = vipAddress == null ? this.vipAddress : vipAddress;
        this.dataCenterType = dataCenterType == null ? this.dataCenterType : dataCenterType.name();
        this.webAdminPort = webAdminPort == null ? this.webAdminPort : webAdminPort;
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

    public int getWebAdminPort() {
        return webAdminPort;
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

    public InstanceInfoFromConfig getMyInstanceInfoConfig() {
        return new InstanceInfoFromConfig(this);
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
        }
    }

    // builder
    public abstract static class EurekaServerConfigBuilder<C extends EurekaServerConfig, B extends EurekaServerConfigBuilder<C, B>>
            extends EurekaCommonConfigBuilder<C, B> {
        protected ResolverType resolverType;
        protected String[] serverList;
        protected Codec codec;
        protected Integer registrationPort;
        protected Integer replicationPort;
        protected Integer discoveryPort;
        protected Integer shutDownPort;
        protected String appName;
        protected String vipAddress;
        protected DataCenterType dataCenterType;
        protected Integer webAdminPort;
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

        public B withShutDownPort(int shutDownPort) {
            this.shutDownPort = shutDownPort;
            return self();
        }

        public B withWebAdminPort(int webAdminPort) {
            this.webAdminPort = webAdminPort;
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
