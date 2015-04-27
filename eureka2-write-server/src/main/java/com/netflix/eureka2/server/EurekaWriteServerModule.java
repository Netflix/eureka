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

import com.google.inject.name.Names;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.SpectatorEurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorWriteServerMetricFactory;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.eviction.EvictionQueueImpl;
import com.netflix.eureka2.registry.eviction.EvictionStrategy;
import com.netflix.eureka2.registry.eviction.EvictionStrategyProvider;
import com.netflix.eureka2.server.audit.AuditServiceController;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.rest.WriteServerRootResource;
import com.netflix.eureka2.server.service.EurekaWriteServerSelfInfoResolver;
import com.netflix.eureka2.server.service.EurekaWriteServerSelfRegistrationService;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.service.bootstrap.PeerRegistryBootstrapService;
import com.netflix.eureka2.server.service.bootstrap.RegistryBootstrapCoordinator;
import com.netflix.eureka2.server.service.bootstrap.RegistryBootstrapService;
import com.netflix.eureka2.server.service.replication.ReplicationService;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.spectator.SpectatorEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerModule extends AbstractEurekaServerModule {

    private final WriteServerConfig config;

    public EurekaWriteServerModule() {
        this(null);
    }

    public EurekaWriteServerModule(WriteServerConfig config) {
        this.config = config;
    }

    @Override
    public void configureEureka() {
        if (config == null) {
            bind(WriteServerConfig.class).asEagerSingleton();
            bind(EurekaCommonConfig.class).to(WriteServerConfig.class);
            bind(EurekaRegistryConfig.class).to(WriteServerConfig.class);
        } else {
            bind(EurekaRegistryConfig.class).toInstance(config);
            bind(EurekaCommonConfig.class).toInstance(config);
            bind(EurekaServerConfig.class).toInstance(config);
            bind(WriteServerConfig.class).toInstance(config);
        }

        bind(IndexRegistry.class).to(IndexRegistryImpl.class).asEagerSingleton();
        bind(SourcedEurekaRegistry.class).annotatedWith(Names.named("delegate")).to(SourcedEurekaRegistryImpl.class).asEagerSingleton();
        bind(SourcedEurekaRegistry.class).to(PreservableEurekaRegistry.class).asEagerSingleton();
        bind(EvictionQueue.class).to(EvictionQueueImpl.class).asEagerSingleton();
        bind(EvictionStrategy.class).toProvider(EvictionStrategyProvider.class);
        bind(AuditServiceController.class).asEagerSingleton();
        bind(RegistryBootstrapCoordinator.class).asEagerSingleton();
        bind(RegistryBootstrapService.class).to(PeerRegistryBootstrapService.class);

        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("registration")).toInstance(new SpectatorEventsListenerFactory("registration-rx-client-", "registration-rx-server-"));
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("discovery")).toInstance(new SpectatorEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("replication")).toInstance(new SpectatorEventsListenerFactory("replication-rx-client-", "replication-rx-server-"));
        bind(TcpRegistrationServer.class).asEagerSingleton();
        bind(TcpDiscoveryServer.class).asEagerSingleton();
        bind(TcpReplicationServer.class).asEagerSingleton();

        bind(ReplicationService.class).asEagerSingleton();

        bind(ExtensionContext.class).asEagerSingleton();

        // REST
        bind(WriteServerRootResource.class).asEagerSingleton();

        // Metrics
        bind(EurekaRegistryMetricFactory.class).to(SpectatorEurekaRegistryMetricFactory.class).asEagerSingleton();

        bind(EurekaServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();
        bind(WriteServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();

        // Self registration
        bind(SelfInfoResolver.class).to(EurekaWriteServerSelfInfoResolver.class).asEagerSingleton();
        bind(SelfRegistrationService.class).to(EurekaWriteServerSelfRegistrationService.class).asEagerSingleton();
    }
}
