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

package com.netflix.rx.eureka.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.rx.eureka.client.transport.ServerConnectionMetrics;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.EurekaRegistryMetrics;
import com.netflix.rx.eureka.server.audit.AuditedRegistry;
import com.netflix.rx.eureka.server.metric.WriteServerMetricFactory;
import com.netflix.rx.eureka.server.replication.ReplicationService;
import com.netflix.rx.eureka.server.service.InterestChannelMetrics;
import com.netflix.rx.eureka.server.service.RegistrationChannelMetrics;
import com.netflix.rx.eureka.server.service.ReplicationChannelMetrics;
import com.netflix.rx.eureka.server.service.SelfRegistrationService;
import com.netflix.rx.eureka.server.service.WriteSelfRegistrationService;
import com.netflix.rx.eureka.server.spi.ExtensionContext;
import com.netflix.rx.eureka.server.transport.ClientConnectionMetrics;
import com.netflix.rx.eureka.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.rx.eureka.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.rx.eureka.server.transport.tcp.replication.TcpReplicationServer;
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

        // Metrics
        bind(ClientConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new ClientConnectionMetrics("eureka2.registration.connection"));
        bind(ClientConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new ClientConnectionMetrics("eureka2.replication.connection"));
        bind(ClientConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new ClientConnectionMetrics("eureka2.discovery.connection"));

        bind(ServerConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new ServerConnectionMetrics("eureka2.client.registration.connection"));
        bind(ServerConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new ServerConnectionMetrics("eureka2.client.discovery.connection"));
        bind(ServerConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new ServerConnectionMetrics("eureka2.client.replication.connection"));

        bind(RegistrationChannelMetrics.class).toInstance(new RegistrationChannelMetrics("eureka2.registration.channel"));
        bind(ReplicationChannelMetrics.class).toInstance(new ReplicationChannelMetrics("eureka2.replication.channel"));
        bind(InterestChannelMetrics.class).toInstance(new InterestChannelMetrics("eureka2.discovery.channel"));

        bind(EurekaRegistryMetrics.class).toInstance(new EurekaRegistryMetrics("eureka2.registry"));
        bind(WriteServerMetricFactory.class).asEagerSingleton();
    }
}
