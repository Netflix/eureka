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
import com.netflix.rx.eureka.client.transport.EurekaClientConnectionMetrics;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.EurekaRegistryImpl;
import com.netflix.rx.eureka.registry.EurekaRegistryMetrics;
import com.netflix.rx.eureka.server.audit.AuditServiceController;
import com.netflix.rx.eureka.server.metric.WriteServerMetricFactory;
import com.netflix.rx.eureka.server.replication.ReplicationService;
import com.netflix.rx.eureka.server.service.InterestChannelMetrics;
import com.netflix.rx.eureka.server.service.RegistrationChannelMetrics;
import com.netflix.rx.eureka.server.service.ReplicationChannelMetrics;
import com.netflix.rx.eureka.server.service.SelfRegistrationService;
import com.netflix.rx.eureka.server.service.WriteSelfRegistrationService;
import com.netflix.rx.eureka.server.spi.ExtensionContext;
import com.netflix.rx.eureka.server.transport.EurekaServerConnectionMetrics;
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

        bind(EurekaRegistry.class).to(EurekaRegistryImpl.class).asEagerSingleton();
        bind(AuditServiceController.class).asEagerSingleton();

        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("registration")).toInstance(new ServoEventsListenerFactory("registration-rx-client-", "registration-rx-server-"));
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("discovery")).toInstance(new ServoEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("replication")).toInstance(new ServoEventsListenerFactory("replication-rx-client-", "replication-rx-server-"));
        bind(TcpRegistrationServer.class).asEagerSingleton();
        bind(TcpDiscoveryServer.class).asEagerSingleton();
        bind(TcpReplicationServer.class).asEagerSingleton();

        bind(ReplicationService.class).asEagerSingleton();

        bind(ExtensionContext.class).asEagerSingleton();

        // Metrics
        bind(EurekaServerConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new EurekaServerConnectionMetrics("registration"));
        bind(EurekaServerConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new EurekaServerConnectionMetrics("replication"));
        bind(EurekaServerConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new EurekaServerConnectionMetrics("discovery"));

        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new EurekaClientConnectionMetrics("registration"));
        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new EurekaClientConnectionMetrics("discovery"));
        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new EurekaClientConnectionMetrics("replication"));

        bind(RegistrationChannelMetrics.class).toInstance(new RegistrationChannelMetrics());
        bind(ReplicationChannelMetrics.class).toInstance(new ReplicationChannelMetrics());
        bind(InterestChannelMetrics.class).toInstance(new InterestChannelMetrics());

        bind(EurekaRegistryMetrics.class).toInstance(new EurekaRegistryMetrics("writerServer"));
        bind(WriteServerMetricFactory.class).asEagerSingleton();
    }
}
