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
import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.server.service.ReadSelfRegistrationService;
import com.netflix.eureka.server.service.SelfRegistrationService;
import com.netflix.eureka.server.transport.tcp.discovery.TcpDiscoveryServer;
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
        bind(MetricEventsListenerFactory.class).toInstance(new ServoEventsListenerFactory());
        bind(TcpDiscoveryServer.class).asEagerSingleton();

        bind(SelfRegistrationService.class).to(ReadSelfRegistrationService.class).asEagerSingleton();

        bind(EurekaRegistry.class).to(EurekaReadServerRegistry.class);
    }
}
