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

package com.netflix.eureka2;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.EurekaBootstrapConfig;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Configuration;

/**
 * @author Tomasz Bak
 */
public class EurekaDashboardConfig extends EurekaBootstrapConfig {

    public static final int DEFAULT_DASHBOARD_PORT = 7001;
    public static final int DEFAULT_WEBSOCKET_PORT = 9000;

    @Configuration("dashboard.http.port")
    protected int dashboardPort = DEFAULT_DASHBOARD_PORT;

    @Configuration("dashboard.websocket.port")
    private int webSocketPort = DEFAULT_WEBSOCKET_PORT;

    @Configuration("writeCluster.discovery.port")
    private int writeClusterDiscoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;

    // For property injection
    protected EurekaDashboardConfig() {
    }

    public EurekaDashboardConfig(DataCenterType dataCenterType, String resolverType, Codec codec,
                                 int shutDownPort, String appName, String vipAddress, String writeClusterDomainName,
                                 String[] writeClusterServers, int webAdminPort, int dashboardPort,
                                 int webSocketPort, int writeClusterDiscoveryPort) {
        super(dataCenterType, resolverType, codec, shutDownPort, appName, vipAddress, writeClusterDomainName,
                writeClusterServers, webAdminPort);
        this.dashboardPort = dashboardPort;
        this.webSocketPort = webSocketPort;
        this.writeClusterDiscoveryPort = writeClusterDiscoveryPort;
    }

    public int getDashboardPort() {
        return dashboardPort;
    }

    public int getWebSocketPort() {
        return webSocketPort;
    }

    public int getWriteClusterDiscoveryPort() {
        return writeClusterDiscoveryPort;
    }

    public static class EurekaDashboardConfigBuilder extends EurekaBootstrapConfigBuilder<EurekaDashboardConfig, EurekaDashboardConfigBuilder> {

        private int dashboardPort = DEFAULT_DASHBOARD_PORT;
        private int webSocketPort = DEFAULT_WEBSOCKET_PORT;
        private int writeClusterDiscoveryPort = EurekaTransports.DEFAULT_DISCOVERY_PORT;

        public EurekaDashboardConfigBuilder withDashboardPort(int port) {
            this.dashboardPort = port;
            return this;
        }

        public EurekaDashboardConfigBuilder withWebSocketPort(int port) {
            this.webSocketPort = port;
            return this;
        }

        public EurekaDashboardConfigBuilder withWriteClusterDiscoveryPort(int writeClusterDiscoveryPort) {
            this.writeClusterDiscoveryPort = writeClusterDiscoveryPort;
            return this;
        }

        @Override
        public EurekaDashboardConfig build() {
            return new EurekaDashboardConfig(dataCenterType, resolverType, codec, shutDownPort, appName, vipAddress,
                    writeClusterDomainName, writeClusterServers, webAdminPort, dashboardPort, webSocketPort,
                    writeClusterDiscoveryPort);
        }
    }
}
