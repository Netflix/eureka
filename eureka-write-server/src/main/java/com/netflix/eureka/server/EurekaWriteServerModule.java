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

package com.netflix.eureka.server;

import com.google.inject.AbstractModule;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.server.audit.AuditedRegistry;
import com.netflix.eureka.server.replication.ReplicationService;
import com.netflix.eureka.server.service.SelfRegistrationService;
import com.netflix.eureka.server.service.WriteSelfRegistrationService;
import com.netflix.eureka.server.spi.ExtensionContext;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka.server.transport.tcp.replication.TcpReplicationServer;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.servo.ServoEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerModule extends AbstractModule {

    private final WriteServerConfig config;

    public EurekaWriteServerModule() {
        this(null);
    }

    public EurekaWriteServerModule(WriteServerConfig config) {
        this.config = config;
    }

    @Override
    public void configure() {
        if (config == null) {
            bind(EurekaBootstrapConfig.class).to(WriteServerConfig.class).asEagerSingleton();
        } else {
            bind(EurekaBootstrapConfig.class).toInstance(config);
            bind(WriteServerConfig.class).toInstance(config);
        }
        bind(SelfRegistrationService.class).to(WriteSelfRegistrationService.class).asEagerSingleton();

        bind(EurekaRegistry.class).to(AuditedRegistry.class);

        bind(MetricEventsListenerFactory.class).toInstance(new ServoEventsListenerFactory());
        bind(TcpRegistrationServer.class).asEagerSingleton();
        bind(TcpDiscoveryServer.class).asEagerSingleton();
        bind(TcpReplicationServer.class).asEagerSingleton();

        bind(ReplicationService.class).asEagerSingleton();

        bind(ExtensionContext.class).asEagerSingleton();
    }
}
