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

import javax.inject.Singleton;

import com.google.inject.Provides;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.registry.EurekaReadServerRegistryView;
import com.netflix.eureka2.server.service.EurekaReadServerSelfInfoResolver;
import com.netflix.eureka2.server.service.EurekaReadServerSelfRegistrationService;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.transport.EurekaTransportServer;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerModule extends AbstractEurekaServerModule {

    @Override
    public void configure() {
        bindBase();
        bindMetricFactories();
        bindSelfInfo();
        bindClients();

        bind(EurekaClientTransportFactory.class).toInstance(EurekaClientTransportFactory.getDefaultFactory());
        bind(EurekaServerTransportFactory.class).toInstance(EurekaServerTransportFactory.getDefaultFactory());

        bindInterestComponents();

        bindRegistryComponents();

        // read servers specific stuff
        bind(ExtensionContext.class).asEagerSingleton();
        bind(ServerType.class).toInstance(ServerType.Read);
        bind(AbstractEurekaServer.class).to(EurekaReadServer.class);
    }

    protected void bindSelfInfo() {
        bind(SelfInfoResolver.class).to(EurekaReadServerSelfInfoResolver.class);
        bind(SelfRegistrationService.class).to(EurekaReadServerSelfRegistrationService.class);
    }

    protected void bindClients() {
        bind(EurekaRegistrationClient.class).toProvider(EurekaRegistrationClientProvider.class);
        bind(EurekaInterestClient.class).toProvider(FullFetchInterestClientProvider.class);
    }

    @Provides
    @Singleton
    public EurekaTransportServer getTransportServer(EurekaServerTransportFactory transportFactory,
                                                    EurekaServerTransportConfig config,
                                                    EurekaInstanceInfoConfig instanceInfoConfig,
                                                    EurekaRegistryView registryView) {
        return new EurekaTransportServer(transportFactory, config, null, null, null, registryView, instanceInfoConfig, Schedulers.computation());
    }

    protected void bindRegistryComponents() {
        bind(EurekaRegistryView.class).to(EurekaReadServerRegistryView.class);
    }

    public static EurekaReadServerModule withClients(final EurekaRegistrationClient registrationClient, final EurekaInterestClient interestClient) {
        return new EurekaReadServerModule() {

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
