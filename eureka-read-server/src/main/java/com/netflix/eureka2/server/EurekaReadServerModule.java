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

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.metric.EurekaClientRegistryMetrics;
import com.netflix.eureka2.client.metric.EurekaClientConnectionMetrics;
import com.netflix.eureka2.server.metric.InterestChannelMetrics;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaReadServerRegistry;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.service.ReadSelfRegistrationService;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.servo.ServoEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerModule extends AbstractModule {

    private final EurekaServerConfig config;
    private final EurekaClient eurekaClient;

    public EurekaReadServerModule() {
        this(null);
    }

    public EurekaReadServerModule(EurekaServerConfig config) {
        this(config, null);
    }

    public EurekaReadServerModule(EurekaServerConfig config, EurekaClient eurekaClient) {
        this.config = config;
        this.eurekaClient = eurekaClient;
    }

    @Override
    public void configure() {
        if (config == null) {
            bind(EurekaServerConfig.class).asEagerSingleton();
        } else {
            bind(EurekaCommonConfig.class).toInstance(config);
            bind(EurekaServerConfig.class).toInstance(config);
        }
        if (eurekaClient == null) {
            bind(EurekaClient.class).toProvider(EurekaClientProvider.class);
        } else {
            bind(EurekaClient.class).toInstance(eurekaClient);
        }

        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("discovery")).toInstance(new ServoEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(TcpDiscoveryServer.class).asEagerSingleton();

        bind(SelfRegistrationService.class).to(ReadSelfRegistrationService.class).asEagerSingleton();

        bind(EurekaServerRegistry.class).to(EurekaReadServerRegistry.class);

        // Metrics
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new MessageConnectionMetrics("registration"));
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new MessageConnectionMetrics("replication"));
        bind(MessageConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new MessageConnectionMetrics("discovery"));

        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("registration")).toInstance(new EurekaClientConnectionMetrics("registration"));
        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("discovery")).toInstance(new EurekaClientConnectionMetrics("discovery"));
        bind(EurekaClientConnectionMetrics.class).annotatedWith(Names.named("replication")).toInstance(new EurekaClientConnectionMetrics("replication"));

        bind(InterestChannelMetrics.class).toInstance(new InterestChannelMetrics());

        bind(EurekaClientRegistryMetrics.class).toInstance(new EurekaClientRegistryMetrics("readServer"));
        bind(EurekaServerMetricFactory.class).asEagerSingleton();
    }
}
