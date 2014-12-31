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

import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.governator.annotations.Configuration;

/**
 * @author Tomasz Bak
 */
public class WriteServerConfig extends EurekaServerConfig implements EurekaRegistryConfig {

    public static final long DEFAULT_EVICTION_TIMEOUT = 30000;
    public static final long DEFAULT_REPLICATION_RECONNECT_DELAY_MILLIS = 30000;

    @Configuration("eureka.services.registration.port")
    protected int registrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;

    @Configuration("eureka.services.replication.port")
    protected int replicationPort = EurekaTransports.DEFAULT_REPLICATION_PORT;

    // replication configs
    @Configuration("eureka.services.replication.reconnectDelayMillis")
    protected long replicationReconnectDelayMillis = DEFAULT_REPLICATION_RECONNECT_DELAY_MILLIS;

    // registry configs
    @Configuration("eureka.registry.evictionTimeoutMs")
    protected long evictionTimeoutMs = DEFAULT_EVICTION_TIMEOUT;

    @Configuration("eureka.registry.evictionStrategy.type")
    protected String evictionStrategyType = EvictionStrategyProvider.StrategyType.PercentageDrop.name();

    @Configuration("eureka.registry.evictionStrategy.value")
    protected String evictionStrategyValue = "20";


    // For property injection
    protected WriteServerConfig() {
    }

    public WriteServerConfig(ResolverType resolverType, String[] serverList, Codec codec, String appName,
                             String vipAddress, DataCenterType dataCenterType, Integer shutDownPort,
                             Integer webAdminPort, Integer registrationPort, Integer replicationPort,
                             Integer discoveryPort, Long replicationReconnectDelayMillis, Long evictionTimeoutMs,
                             StrategyType evictionStrategyType, String evictionStrategyValue) {
        super(resolverType, serverList, codec, appName, vipAddress, dataCenterType, shutDownPort, webAdminPort, discoveryPort);

        this.registrationPort = registrationPort == null ? this.registrationPort : registrationPort;
        this.replicationPort = replicationPort == null ? this.replicationPort : replicationPort;

        this.replicationReconnectDelayMillis = replicationReconnectDelayMillis == null ? this.replicationReconnectDelayMillis : replicationReconnectDelayMillis;
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

    public long getReplicationReconnectDelayMillis() {
        return replicationReconnectDelayMillis;
    }

    @Override
    public long getEvictionTimeoutMs() {
        return evictionTimeoutMs;
    }

    @Override
    public EvictionStrategyProvider.StrategyType getEvictionStrategyType() {
        EvictionStrategyProvider.StrategyType type;
        try {
            type = EvictionStrategyProvider.StrategyType.valueOf(evictionStrategyType);
        } catch (Exception e) {
            type = EvictionStrategyProvider.StrategyType.PercentageDrop;
        }

        return type;
    }

    @Override
    public String getEvictionStrategyValue() {
        return evictionStrategyValue;
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
                    codec,
                    appName,
                    vipAddress,
                    dataCenterType,
                    shutDownPort,
                    webAdminPort,
                    registrationPort,
                    replicationPort,
                    discoveryPort,
                    replicationReconnectDelayMillis,
                    evictionTimeoutMs,
                    evictionStrategyType,
                    evictionStrategyValue
            );
        }
    }

    // builder
    public abstract static class AbstractWriteServerConfigBuilder<C extends WriteServerConfig, B extends AbstractWriteServerConfigBuilder<C, B>>
            extends AbstractEurekaServerConfigBuilder<C, B> {
        protected Integer registrationPort;
        protected Integer replicationPort;
        protected Long replicationReconnectDelayMillis;
        protected Long evictionTimeoutMs;
        protected EvictionStrategyProvider.StrategyType evictionStrategyType;
        protected String evictionStrategyValue;

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
