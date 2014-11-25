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
import com.netflix.eureka2.transport.EurekaTransports.Codec;

/**
 * This class contains essential configuration data that are required during Eureka write server
 * bootstrapping. Multiple sources of this data are supported, like command line arguments,
 * property configuration file and archaius.
 *
 * @author Tomasz Bak
 */
public class WriteServerConfig extends EurekaBootstrapConfig {

    public WriteServerConfig() {
    }

    public WriteServerConfig(DataCenterType dataCenterType, String resolverType,
                             int writeServerPort, int replicationPort, int readServerPort, Codec codec, int shutDownPort,
                             String appName, String vipAddress, String writeClusterDomainName,
                             String[] writeClusterServers, int webAdminPort,
                             long registryEvictionTimeout, String evictionStrategyType, String evictionStrategyValue) {
        super(dataCenterType, resolverType, writeServerPort, replicationPort, readServerPort, codec, shutDownPort,
                appName, vipAddress, writeClusterDomainName, writeClusterServers, webAdminPort,
                registryEvictionTimeout, evictionStrategyType, evictionStrategyValue);
    }

    public static class WriteServerConfigBuilder extends EurekaBootstrapConfigBuilder<WriteServerConfig, WriteServerConfigBuilder> {

        @Override
        public WriteServerConfig build() {
            return new WriteServerConfig(dataCenterType, resolverType,
                    registrationPort, replicationPort, discoveryPort, codec, shutDownPort,
                    appName, vipAddress, writeClusterDomainName, writeClusterServers, webAdminPort,
                    evictionTimeout, evictionStrategyType.name(), evictionStrategyValue);
        }
    }
}
