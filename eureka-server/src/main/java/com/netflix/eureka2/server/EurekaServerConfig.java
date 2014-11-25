/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.server;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Configuration;

/**
 * This class contains essential configuration data that are required during Eureka read/write server
 * bootstrapping. Multiple sources of this data are supported, like command line arguments,
 * property configuration file and archaius.
 *
 * @author Tomasz Bak
 */
public abstract class EurekaServerConfig extends EurekaBootstrapConfig {

    public static final long DEFAULT_EVICTION_TIMEOUT = 30000;

    @Configuration("services.registration.port")
    private int registrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;

    @Configuration("services.replication.port")
    private int replicationPort = EurekaTransports.DEFAULT_REPLICATION_PORT;

    @Configuration("services.discovery.port")
    private int discoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;

    @Configuration("registry.evictionTimeout")
    private long evictionTimeout = DEFAULT_EVICTION_TIMEOUT;

    @Configuration("registry.evictionStrategy.type")
    private String evictionStrategyType = StrategyType.PercentageDrop.name();

    @Configuration("registry.evictionStrategy.value")
    private String evictionStrategyValue = "20";

    // For property injection
    protected EurekaServerConfig() {
    }

    protected EurekaServerConfig(DataCenterType dataCenterType, String resolverType,
                                 int registrationPort, int replicationPort, int discoveryPort, Codec codec, int shutDownPort,
                                 String appName, String vipAddress, String writeClusterDomainName,
                                 String[] writeClusterServers, int webAdminPort,
                                 long evictionTimeout, String evictionStrategyType, String evictionStrategyValue) {
        super(dataCenterType, resolverType, codec, shutDownPort, appName, vipAddress, writeClusterDomainName, writeClusterServers, webAdminPort);
        this.registrationPort = registrationPort;
        this.replicationPort = replicationPort;
        this.discoveryPort = discoveryPort;
        this.evictionTimeout = evictionTimeout;
        this.evictionStrategyType = evictionStrategyType;
        this.evictionStrategyValue = evictionStrategyValue;
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

    public long getEvictionTimeout() {
        return evictionTimeout;
    }

    public String getEvictionStrategyType() {
        return evictionStrategyType;
    }

    public String getEvictionStrategyValue() {
        return evictionStrategyValue;
    }

    public abstract static class EurekaServerConfigBuilder<C extends EurekaServerConfig, B extends EurekaServerConfigBuilder<C, B>>
            extends EurekaBootstrapConfigBuilder<C, B> {
        protected int registrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;
        protected int replicationPort = EurekaTransports.DEFAULT_REPLICATION_PORT;
        protected int discoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;
        protected long evictionTimeout = DEFAULT_EVICTION_TIMEOUT;
        protected StrategyType evictionStrategyType = StrategyType.PercentageDrop;
        protected String evictionStrategyValue = "20";

        protected EurekaServerConfigBuilder() {
        }

        public B withWriteServerPort(int writeServerPort) {
            this.registrationPort = writeServerPort;
            return self();
        }

        public B withReplicationPort(int replicationPort) {
            this.replicationPort = replicationPort;
            return self();
        }

        public B withReadServerPort(int readServerPort) {
            this.discoveryPort = readServerPort;
            return self();
        }

        public B withEvictionTimeout(long evictionTimeout) {
            this.evictionTimeout = evictionTimeout;
            return self();
        }

        public B withEvictionStrategyType(StrategyType strategyType) {
            this.evictionStrategyType = strategyType;
            return self();
        }

        public B withEvictionStrategyValue(String strategyValue) {
            this.evictionStrategyValue = strategyValue;
            return self();
        }
    }
}
