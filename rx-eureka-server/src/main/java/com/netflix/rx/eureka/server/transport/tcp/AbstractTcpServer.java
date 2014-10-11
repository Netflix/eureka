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

package com.netflix.rx.eureka.server.transport.tcp;

import javax.annotation.PreDestroy;

import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.server.EurekaBootstrapConfig;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.server.RxServer;

/**
 * @author Tomasz Bak
 */
public class AbstractTcpServer {

    protected final EurekaBootstrapConfig config;
    protected final EurekaRegistry eurekaRegistry;
    protected final MetricEventsListenerFactory servoEventsListenerFactory;
    protected RxServer<Object, Object> server;

    public AbstractTcpServer(EurekaRegistry eurekaRegistry, MetricEventsListenerFactory servoEventsListenerFactory, EurekaBootstrapConfig config) {
        this.eurekaRegistry = eurekaRegistry;
        this.servoEventsListenerFactory = servoEventsListenerFactory;
        this.config = config;
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (server != null) {
            server.shutdown();
        }
    }
}
