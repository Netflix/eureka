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
import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.client.transport.EurekaClientConnectionMetrics;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.EurekaRegistryMetrics;
import com.netflix.rx.eureka.server.metric.EurekaServerMetricFactory;
import com.netflix.rx.eureka.server.service.InterestChannelMetrics;
import com.netflix.rx.eureka.server.service.ReadSelfRegistrationService;
import com.netflix.rx.eureka.server.service.RegistrationChannelMetrics;
import com.netflix.rx.eureka.server.service.ReplicationChannelMetrics;
import com.netflix.rx.eureka.server.service.SelfRegistrationService;
import com.netflix.rx.eureka.server.transport.EurekaServerConnectionMetrics;
import com.netflix.rx.eureka.server.transport.tcp.discovery.TcpDiscoveryServer;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.servo.ServoEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerModule extends AbstractModule {

    private final ReadServerConfig config;
    private final EurekaClient eurekaClient;

    public EurekaReadServerModule() {
        this(null);
    }

    public EurekaReadServerModule(ReadServerConfig config) {
        this(config, null);
    }

    public EurekaReadServerModule(ReadServerConfig config, EurekaClient eurekaClient) {
        this.config = config;
        this.eurekaClient = eurekaClient;
    }

    @Override
    public void configure() {
        if (config == null) {
            bind(EurekaBootstrapConfig.class).to(ReadServerConfig.class).asEagerSingleton();
        } else {
            bind(EurekaBootstrapConfig.class).toInstance(config);
            bind(ReadServerConfig.class).toInstance(config);
        }
        if (eurekaClient == null) {
            bind(EurekaClient.class).toProvider(EurekaClientProvider.class);
        } else {
            bind(EurekaClient.class).toInstance(eurekaClient);
        }
        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("discovery")).toInstance(new ServoEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(TcpDiscoveryServer.class).asEagerSingleton();

        bind(SelfRegistrationService.class).to(ReadSelfRegistrationService.class).asEagerSingleton();

        bind(EurekaRegistry.class).to(EurekaReadServerRegistry.class);

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

        bind(EurekaRegistryMetrics.class).toInstance(new EurekaRegistryMetrics("readServer"));
        bind(EurekaServerMetricFactory.class).asEagerSingleton();
    }
}
