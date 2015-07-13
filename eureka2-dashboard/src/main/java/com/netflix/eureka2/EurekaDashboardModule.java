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

import com.google.inject.Provides;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.AbstractEurekaServerModule;
import com.netflix.eureka2.server.EurekaInterestClientProvider;
import com.netflix.eureka2.server.EurekaRegistrationClientProvider;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;

import javax.inject.Singleton;

/**
 * @author David Liu
 */
public class EurekaDashboardModule extends AbstractEurekaServerModule {

    @Override
    protected void configure() {
        bindMetricFactories();
        bindSelfInfo();
        bindClients();

        bind(DashboardHttpServer.class).asEagerSingleton();
        bind(WebSocketServer.class).asEagerSingleton();

        bind(ServerType.class).toInstance(ServerType.Dashboard);
        bind(AbstractEurekaServer.class).to(EurekaDashboardServer.class);
    }

    protected void bindSelfInfo() {
        bind(SelfRegistrationService.class).to(DashboardServerSelfRegistrationService.class).asEagerSingleton();
        bind(SelfInfoResolver.class).to(DashboardServerSelfInfoResolver.class).asEagerSingleton();
    }

    protected void bindClients() {
        bind(EurekaRegistrationClient.class).toProvider(EurekaRegistrationClientProvider.class);
        bind(EurekaInterestClient.class).toProvider(EurekaInterestClientProvider.class);
    }

    public static EurekaDashboardModule withClients(final EurekaRegistrationClient registrationClient, final EurekaInterestClient interestClient) {
        return new EurekaDashboardModule() {

            @Override
            protected void bindClients() {
                // do nothing
            }

            @Provides
            @Singleton
            public EurekaRegistrationClient getEurekaRegistrationClient() {
                return registrationClient;
            }

            @Provides
            @Singleton
            public EurekaInterestClient getEurekaInterestClient() {
                return interestClient;
            }
        };
    }
}
