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
import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.client.registration.EurekaRegistrationClient;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.SpectatorEurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.metric.client.SpectatorEurekaClientMetricFactory;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorEurekaServerMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.registry.EurekaReadServerRegistry;
import com.netflix.eureka2.server.service.EurekaReadServerSelfInfoResolver;
import com.netflix.eureka2.server.service.EurekaReadServerSelfRegistrationService;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.spectator.SpectatorEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerModule extends AbstractModule {

    private final EurekaServerConfig config;
    private final EurekaRegistrationClient registrationClient;
    private final EurekaInterestClient interestClient;

    public EurekaReadServerModule() {
        this(null);
    }

    public EurekaReadServerModule(EurekaServerConfig config) {
        this(config, null, null);
    }

    public EurekaReadServerModule(EurekaServerConfig config,
                                  EurekaRegistrationClient registrationClient,
                                  EurekaInterestClient interestClient) {
        this.config = config;
        this.registrationClient = registrationClient;
        this.interestClient = interestClient;
    }

    @Override
    public void configure() {
        if (config == null) {
            bind(EurekaServerConfig.class).asEagerSingleton();
            bind(EurekaCommonConfig.class).to(EurekaServerConfig.class);
        } else {
            bind(EurekaCommonConfig.class).toInstance(config);
            bind(EurekaServerConfig.class).toInstance(config);
        }
        if (registrationClient == null) {
            bind(EurekaRegistrationClient.class).toProvider(RegistrationClientProvider.class);
        } else {
            bind(EurekaRegistrationClient.class).toInstance(registrationClient);
        }
        if (interestClient == null) {
            bind(EurekaInterestClient.class).toProvider(FullFetchInterestClientProvider.class);
        } else {
            bind(EurekaInterestClient.class).toInstance(interestClient);
        }

        bind(MetricEventsListenerFactory.class).annotatedWith(Names.named("discovery")).toInstance(new SpectatorEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
        bind(TcpDiscoveryServer.class).asEagerSingleton();

        bind(SourcedEurekaRegistry.class).to(EurekaReadServerRegistry.class);

        // Metrics
        bind(EurekaClientMetricFactory.class).to(SpectatorEurekaClientMetricFactory.class).asEagerSingleton();
        bind(EurekaServerMetricFactory.class).to(SpectatorEurekaServerMetricFactory.class).asEagerSingleton();
        bind(EurekaRegistryMetricFactory.class).to(SpectatorEurekaRegistryMetricFactory.class).asEagerSingleton();

        bind(SelfInfoResolver.class).to(EurekaReadServerSelfInfoResolver.class).asEagerSingleton();
        bind(SelfRegistrationService.class).to(EurekaReadServerSelfRegistrationService.class).asEagerSingleton();
    }
}
