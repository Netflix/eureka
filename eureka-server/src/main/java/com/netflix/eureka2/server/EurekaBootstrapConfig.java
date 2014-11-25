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
import com.netflix.governator.annotations.Configuration;

/**
 * @author Tomasz Bak
 */
public class EurekaBootstrapConfig {
    @Configuration("writeCluster.resolverType")
    protected String resolverType = "inline";

    @Configuration("services.transport.codec")
    protected String codec = "Avro";

    @Configuration("services.shutdown.port")
    protected int shutDownPort = 7700;

    @Configuration("dataCenter.type")
    protected String dataCenterType = DataCenterType.Basic.name();

    @Configuration("applicationName")
    protected String appName = "eurekaWriteCluster";

    @Configuration("vipAddress")
    private String vipAddress;

    @Configuration("writeCluster.servers")
    protected String[] writeClusterServers = {"localhost"};

    @Configuration("writeCluster.domainName")
    protected String writeClusterDomainName;

    @Configuration("netflix.platform.admin.resources.port")
    protected int webAdminPort = 8077;

    // For property injection
    protected EurekaBootstrapConfig() {
    }

    protected EurekaBootstrapConfig(DataCenterType dataCenterType, String resolverType,
                                    Codec codec, int shutDownPort,
                                    String appName, String vipAddress, String writeClusterDomainName,
                                    String[] writeClusterServers, int webAdminPort) {
        this.resolverType = resolverType;
        this.shutDownPort = shutDownPort;
        this.appName = appName;
        this.vipAddress = vipAddress;
        this.writeClusterDomainName = writeClusterDomainName;
        this.writeClusterServers = writeClusterServers;
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

    public String[] getWriteClusterServers() {
        return writeClusterServers;
    }

    public String getWriteClusterDomainName() {
        return writeClusterDomainName;
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

        public B withWriteClusterAddresses(String[] rest) {
            this.writeClusterServers = rest;
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
}
