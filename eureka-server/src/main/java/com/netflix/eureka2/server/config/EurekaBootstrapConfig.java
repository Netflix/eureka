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
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Configuration;

/**
 * @author Tomasz Bak
 */
public class EurekaBootstrapConfig {

    // common write server configs
    @Configuration("common.writeCluster.resolverType")
    protected String resolverType = "inline";

    @Configuration("common.writeCluster.servers")
    protected String[] inlineWriteClusterServers = {"localhost:12102:12103:12104"};

    @Configuration("common.writeCluster.domainName")
    protected String dnsWriteClusterServer;

    // self configs
    @Configuration("services.transport.codec")
    protected String codec = "Avro";

    @Configuration("services.shutdown.port")
    protected int shutDownPort = 7700;

    @Configuration("info.dataCenter.type")
    protected String dataCenterType = DataCenterType.Basic.name();

    @Configuration("info.applicationName")
    protected String appName = "eurekaWriteCluster";

    @Configuration("info.vipAddress")
    private String vipAddress;

    @Configuration("netflix.platform.admin.resources.port")
    protected int webAdminPort = 8077;

    // For property injection
    protected EurekaBootstrapConfig() {
    }

    protected EurekaBootstrapConfig(DataCenterType dataCenterType, String resolverType,
                                    Codec codec, int shutDownPort,
                                    String appName, String vipAddress, String dnsWriteClusterServer,
                                    String[] inlineWriteClusterServers, int webAdminPort) {
        this.resolverType = resolverType;
        this.shutDownPort = shutDownPort;
        this.appName = appName;
        this.vipAddress = vipAddress;
        this.dnsWriteClusterServer = dnsWriteClusterServer;
        this.inlineWriteClusterServers = inlineWriteClusterServers;
        this.webAdminPort = webAdminPort;
        this.codec = codec.name();
        this.dataCenterType = dataCenterType.name();
    }

    public DataCenterType getDataCenterType() {
        return DataCenterType.valueOf(dataCenterType);
    }

    public String getResolverType() {
        return resolverType;
    }

    public Codec getCodec() {
        return Codec.valueOf(codec);
    }

    public int getShutDownPort() {
        return shutDownPort;
    }

    public String getAppName() {
        return appName;
    }

    public String getVipAddress() {
        return vipAddress;
    }

    public WriteServerBootstrap[] getWriteClusterServersInline() {
        return WriteServerBootstrap.fromList(inlineWriteClusterServers);
    }

    public WriteServerBootstrap getWriteClusterServerDns() {
        return WriteServerBootstrap.from(dnsWriteClusterServer);
    }

    public int getWebAdminPort() {
        return webAdminPort;
    }

    public abstract static class EurekaBootstrapConfigBuilder<C extends EurekaBootstrapConfig, B extends EurekaBootstrapConfigBuilder<C, B>> {
        protected boolean helpOption;
        protected DataCenterType dataCenterType = DataCenterType.Basic;
        protected String resolverType = "inline";
        protected int shutDownPort = 7700;
        protected String appName;
        protected String vipAddress;
        protected String[] writeClusterServers = {"127.0.0.1"};
        protected String writeClusterDomainName;
        protected Codec codec = Codec.Avro;
        protected int webAdminPort = 8077;

        protected EurekaBootstrapConfigBuilder() {
        }

        public B withHelpOption(boolean helpOption) {
            this.helpOption = helpOption;
            return self();
        }

        public B withResolverType(String resolverType) {
            this.resolverType = resolverType;
            return self();
        }

        public B withWebAdminPort(int webAdminPort) {
            this.webAdminPort = webAdminPort;
            return self();
        }

        public B withShutDownPort(int shutDownPort) {
            this.shutDownPort = shutDownPort;
            return self();
        }

        public B withDataCenterType(DataCenterType dataCenterType) {
            this.dataCenterType = dataCenterType;
            return self();
        }

        public B withAppName(String appName) {
            this.appName = appName;
            return self();
        }

        public B withVipAddress(String vipAddress) {
            this.vipAddress = vipAddress;
            return self();
        }

        public B withWriteClusterServers(String[] servers) {
            this.writeClusterServers = servers;
            return self();
        }

        public B withWriteClusterDomainName(String writeClusterDomainName) {
            this.writeClusterDomainName = writeClusterDomainName;
            return self();
        }

        public B withCodec(Codec codec) {
            this.codec = codec;
            return self();
        }

        public abstract C build();

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
    }

    public static class WriteServerBootstrap {
        private final String hostname;
        private final Integer registrationPort;
        private final Integer discoveryPort;
        private final Integer replicationPort;

        protected static WriteServerBootstrap[] fromList(String... hostnameAndPortsList) {
            WriteServerBootstrap[] servers = new WriteServerBootstrap[hostnameAndPortsList.length];
            for (int i = 0; i < hostnameAndPortsList.length; i++) {
                servers[i] = new WriteServerBootstrap(hostnameAndPortsList[i]);
            }
            return servers;
        }

        protected static WriteServerBootstrap from(String hostnameAndPorts) {
            return new WriteServerBootstrap(hostnameAndPorts);
        }

        private WriteServerBootstrap(String hostnameAndPorts) {
            try {
                String[] parts = hostnameAndPorts.split(":");
                hostname = parts[0];
                registrationPort = Integer.parseInt(parts[1]);
                discoveryPort = Integer.parseInt(parts[2]);
                replicationPort = Integer.parseInt(parts[3]);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        public String getHostname() {
            return hostname;
        }

        public Integer getRegistrationPort() {
            return registrationPort;
        }

        public Integer getDiscoveryPort() {
            return discoveryPort;
        }

        public Integer getReplicationPort() {
            return replicationPort;
        }
    }
}
