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

package com.netflix.eureka2;

import com.google.inject.AbstractModule;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.SpectatorEurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.metric.client.SpectatorEurekaClientMetricFactory;
import com.netflix.eureka2.server.EurekaClientProvider;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;

/**
 * @author Tomasz Bak
 */
public class EurekaDashboardModule extends AbstractModule {

    private final EurekaDashboardConfig config;
    private final EurekaClient eurekaClient;

    public EurekaDashboardModule() {
        this(null);
    }

    public EurekaDashboardModule(EurekaDashboardConfig config) {
        this(config, null);
    }

    public EurekaDashboardModule(EurekaDashboardConfig config, EurekaClient eurekaClient) {
        this.config = config;
        this.eurekaClient = eurekaClient;
    }

    @Override
    protected void configure() {
        if (config == null) {
            bind(EurekaDashboardConfig.class).asEagerSingleton();
            bind(EurekaCommonConfig.class).to(EurekaDashboardConfig.class);
        } else {
            bind(EurekaCommonConfig.class).toInstance(config);
            bind(EurekaDashboardConfig.class).toInstance(config);
        }
        if (eurekaClient == null) {
            bind(EurekaClient.class).toProvider(EurekaClientProvider.class);
        } else {
            bind(EurekaClient.class).toInstance(eurekaClient);
        }

        bind(DashboardHttpServer.class).asEagerSingleton();
        bind(WebSocketServer.class).asEagerSingleton();

        // Self registration
        bind(SelfRegistrationService.class).to(DashboardServerSelfRegistrationService.class).asEagerSingleton();
        bind(SelfInfoResolver.class).to(DashboardServerSelfInfoResolver.class).asEagerSingleton();

        // Metrics
        bind(EurekaClientMetricFactory.class).to(SpectatorEurekaClientMetricFactory.class).asEagerSingleton();
        bind(EurekaRegistryMetricFactory.class).to(SpectatorEurekaRegistryMetricFactory.class).asEagerSingleton();
    }
}
