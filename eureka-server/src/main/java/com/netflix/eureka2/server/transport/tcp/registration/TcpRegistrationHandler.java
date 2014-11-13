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

package com.netflix.eureka2.server.transport.tcp.registration;

import com.google.inject.Inject;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.EvictionQueue;
import com.netflix.eureka2.server.service.EurekaServerService;
import com.netflix.eureka2.server.service.EurekaServiceImpl;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.transport.base.HeartBeatConnection;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class TcpRegistrationHandler implements ConnectionHandler<Object, Object> {

    private final EurekaServerRegistry<InstanceInfo> registry;
    private final EvictionQueue evictionQueue;
    private final EurekaServerMetricFactory metricFactory;

    @Inject
    public TcpRegistrationHandler(EurekaServerRegistry registry, EvictionQueue evictionQueue, EurekaServerMetricFactory metricFactory) {
        this.registry = registry;
        this.evictionQueue = evictionQueue;
        this.metricFactory = metricFactory;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        MessageConnection broker = new HeartBeatConnection(
                new BaseMessageConnection("registration", connection, metricFactory.getRegistrationConnectionMetrics()),
                30000, 3,
                Schedulers.computation()
        );
        final EurekaServerService service = new EurekaServiceImpl(registry, evictionQueue, broker, metricFactory);
        return service.newRegistrationChannel()
                .asLifecycleObservable(); // Since this is a discovery handler which only handles interest subscriptions,
        // the channel is created on connection accept.
    }
}
