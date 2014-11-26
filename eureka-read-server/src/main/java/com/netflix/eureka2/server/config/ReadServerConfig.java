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

package com.netflix.eureka2.server.config;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Configuration;

/**
 * @author Tomasz Bak
 */
public class ReadServerConfig extends EurekaServerConfig {

    @Configuration("writeCluster.registration.port")
    private int writeClusterRegistrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;

    @Configuration("writeCluster.discovery.port")
    private int writeClusterDiscoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;

    public ReadServerConfig() {
    }

    public ReadServerConfig(DataCenterType dataCenterType, String resolverType,
                            int readServerPort, Codec codec, int shutDownPort,
                            String appName, String vipAddress, String writeClusterDomainName,
                            String[] writeClusterServers, int writeClusterRegistrationPort,
                            int writeClusterDiscoveryPort, int webAdminPort,
                            long registryEvictionTimeout, String evictionStrategyType, String evictionStrategyValue) {
        super(dataCenterType, resolverType, -1, -1, readServerPort, codec, shutDownPort, appName, vipAddress,
                writeClusterDomainName, writeClusterServers, webAdminPort,
                registryEvictionTimeout, evictionStrategyType, evictionStrategyValue);
        this.writeClusterRegistrationPort = writeClusterRegistrationPort;
        this.writeClusterDiscoveryPort = writeClusterDiscoveryPort;
    }

    public int getWriteClusterRegistrationPort() {
        return writeClusterRegistrationPort;
    }

    public int getWriteClusterDiscoveryPort() {
        return writeClusterDiscoveryPort;
    }

    public static class ReadServerConfigBuilder extends EurekaServerConfigBuilder<ReadServerConfig, ReadServerConfigBuilder> {

        private int writeClusterRegistrationPort;
        private int writeClusterDiscoveryPort;

        public ReadServerConfigBuilder withWriteClusterRegistrationPort(int writeClusterRegistrationPort) {
            this.writeClusterRegistrationPort = writeClusterRegistrationPort;
            return this;
        }

        public ReadServerConfigBuilder withWriteClusterDiscoveryPort(int writeClusterDiscoveryPort) {
            this.writeClusterDiscoveryPort = writeClusterDiscoveryPort;
            return this;
        }

        @Override
        public ReadServerConfig build() {
            return new ReadServerConfig(dataCenterType, resolverType, discoveryPort, codec, shutDownPort, appName, vipAddress,
                    writeClusterDomainName, writeClusterServers, writeClusterRegistrationPort,
                    writeClusterDiscoveryPort, webAdminPort,
                    evictionTimeout, evictionStrategyType.name(), evictionStrategyValue);
        }
    }
}
