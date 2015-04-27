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

import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolvers.ResolverType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.governator.annotations.Configuration;

/**
 * @author Tomasz Bak
 */
public class WriteServerConfig extends EurekaServerConfig {

    public static final long DEFAULT_REPLICATION_RECONNECT_DELAY_MILLIS = 30000;

    @Configuration("eureka.services.registration.port")
    protected int registrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;

    @Configuration("eureka.services.replication.port")
    protected int replicationPort = EurekaTransports.DEFAULT_REPLICATION_PORT;

    // replication configs
    @Configuration("eureka.services.replication.reconnectDelayMillis")
    protected long replicationReconnectDelayMillis = DEFAULT_REPLICATION_RECONNECT_DELAY_MILLIS;

    @Configuration("eureka.services.bootstrap.enabled")
    protected boolean bootstrapEnabled;

    @Configuration("eureka.services.bootstrap.resolverType")
    protected String bootstrapResolverType;

    @Configuration("eureka.services.bootstrap.serverList")
    protected String[] bootstrapServerList;

    // For property injection
    protected WriteServerConfig() {
    }

    public WriteServerConfig(
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
            String evictionStrategyValue,
            // write server configs
            int registrationPort,
            int replicationPort,
            long replicationReconnectDelayMillis,
            boolean bootstrapEnabled,
            ResolverType bootstrapResolverType,
            String[] bootstrapServerList

    ) {
        super(resolverType, serverList, appName, vipAddress, readClusterVipAddress,
                dataCenterType, dataCenterResolveIntervalSec,
                httpPort, shutDownPort, webAdminPort, discoveryPort, heartbeatIntervalMs, connectionAutoTimeoutMs, codec,
                evictionTimeoutMs, evictionStrategyType, evictionStrategyValue);

        this.registrationPort = registrationPort;
        this.replicationPort = replicationPort;
        this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapResolverType = bootstrapResolverType == null ? this.bootstrapResolverType : bootstrapResolverType.name();
        this.bootstrapServerList = bootstrapServerList;
    }

    public int getRegistrationPort() {
        return registrationPort;
    }

    public int getReplicationPort() {
        return replicationPort;
    }

    public long getReplicationReconnectDelayMillis() {
        return replicationReconnectDelayMillis;
    }

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public ResolverType getBootstrapResolverType() {
        ResolverType result;
        try {
            result = ResolverType.valueOfIgnoreCase(bootstrapResolverType);
        } catch (Exception e) {
            return ResolverType.Fixed;
        }
        return result;
    }

    public String[] getBootstrapServerList() {
        return bootstrapServerList;
    }

    public static WriteServerConfigBuilder writeBuilder() {
        return new WriteServerConfigBuilder();
    }

    // default builder
    public static class WriteServerConfigBuilder
            extends AbstractWriteServerConfigBuilder<WriteServerConfig, WriteServerConfigBuilder> {

        @Override
        public WriteServerConfig build() {
            return new WriteServerConfig(
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
                    evictionStrategyValue,
                    // write server configs
                    registrationPort,
                    replicationPort,
                    replicationReconnectDelayMillis,
                    bootstrapEnabled,
                    bootstrapResolverType,
                    bootstrapServerList
                    );
        }
    }

    // builder
    public abstract static class AbstractWriteServerConfigBuilder<C extends WriteServerConfig, B extends AbstractWriteServerConfigBuilder<C, B>>
            extends AbstractEurekaServerConfigBuilder<C, B> {
        protected int registrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;
        protected int replicationPort = EurekaTransports.DEFAULT_REPLICATION_PORT;
        protected long replicationReconnectDelayMillis = DEFAULT_REPLICATION_RECONNECT_DELAY_MILLIS;
        protected boolean bootstrapEnabled;
        protected ResolverType bootstrapResolverType;
        protected String[] bootstrapServerList;

        protected AbstractWriteServerConfigBuilder() {
        }

        public B withRegistrationPort(int writeServerPort) {
            this.registrationPort = writeServerPort;
            return self();
        }

        public B withReplicationPort(int replicationPort) {
            this.replicationPort = replicationPort;
            return self();
        }

        public B withReplicationRetryMillis(long replicationReconnectDelayMillis) {
            this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
            return self();
        }

        public B withBootstrapEnabled(boolean enabled) {
            this.bootstrapEnabled = enabled;
            return self();
        }

        public B withBootstrapResolverType(ResolverType bootstrapResolverType) {
            this.bootstrapResolverType = bootstrapResolverType;
            return self();
        }

        public B withBootstrapServerList(String[] bootstrapServerList) {
            this.bootstrapServerList = bootstrapServerList;
            return self();
        }
    }
}
