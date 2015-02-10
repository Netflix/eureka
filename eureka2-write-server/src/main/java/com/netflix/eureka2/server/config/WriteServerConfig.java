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
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider.StrategyType;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
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


    // For property injection
    protected WriteServerConfig() {
    }

    public WriteServerConfig(
            ResolverType resolverType,
            String[] serverList,
            String appName,
            String vipAddress,
            DataCenterType dataCenterType,
            int shutDownPort,
            int webAdminPort,
            int discoveryPort,
            long heartbeatIntervalMs,
            long connectionAutoTimeoutMs,
            Codec codec,
            long evictionTimeoutMs,
            StrategyType evictionStrategyType,
            String evictionStrategyValue,
            // write server configs
            int registrationPort,
            int replicationPort,
            long replicationReconnectDelayMillis
    ) {
        super(resolverType, serverList, appName, vipAddress, dataCenterType, shutDownPort, webAdminPort, discoveryPort,
                heartbeatIntervalMs, connectionAutoTimeoutMs, codec, evictionTimeoutMs, evictionStrategyType, evictionStrategyValue);

        this.registrationPort = registrationPort;
        this.replicationPort = replicationPort;
        this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
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
                    dataCenterType,
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
                    replicationReconnectDelayMillis
            );
        }
    }

    // builder
    public abstract static class AbstractWriteServerConfigBuilder<C extends WriteServerConfig, B extends AbstractWriteServerConfigBuilder<C, B>>
            extends AbstractEurekaServerConfigBuilder<C, B> {
        protected int registrationPort = EurekaTransports.DEFAULT_REGISTRATION_PORT;
        protected int replicationPort = EurekaTransports.DEFAULT_REPLICATION_PORT;
        protected long replicationReconnectDelayMillis = DEFAULT_REPLICATION_RECONNECT_DELAY_MILLIS;

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
    }
}
