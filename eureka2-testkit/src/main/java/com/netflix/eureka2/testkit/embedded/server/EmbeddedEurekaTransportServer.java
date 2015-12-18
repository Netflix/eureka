/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.testkit.embedded.server;

import com.google.inject.Provider;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.transport.EurekaTransportServer;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import rx.schedulers.Schedulers;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 */
@Singleton
public class EmbeddedEurekaTransportServer extends EurekaTransportServer {

    private final NetworkRouter networkRouter;

    private int proxyPort;

    @Inject
    public EmbeddedEurekaTransportServer(EurekaServerTransportFactory transportFactory,
                                         EurekaServerTransportConfig config,
                                         @Named(Names.REGISTRATION) Provider<EurekaRegistrationProcessor> registrationProcessor,
                                         @Named(Names.REGISTRATION) MetricEventsListenerFactory servoEventsListenerFactory,
                                         EurekaRegistry registry,
                                         EurekaRegistryView registryView,
                                         EurekaInstanceInfoConfig instanceInfoConfig,
                                         NetworkRouter networkRouter) {
        super(transportFactory, config, registrationProcessor, servoEventsListenerFactory, registry, registryView, instanceInfoConfig, Schedulers.computation());
        this.networkRouter = networkRouter;
    }

    @PostConstruct
    public void start() {
        proxyPort = networkRouter.bridgeTo(super.getServerPort());
    }

    @PreDestroy
    public void stop() {
        networkRouter.removeBridgeTo(super.getServerPort());
    }

    @Override
    public int getServerPort() {
        return proxyPort;
    }
}
