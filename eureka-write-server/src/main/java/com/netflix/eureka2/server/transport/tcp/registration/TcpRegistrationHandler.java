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
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.metric.WriteServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.channel.ServerChannelFactory;
import com.netflix.eureka2.server.channel.ServerChannelFactoryImpl;
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

    // FIXME add an override from sys property for now
    private static final long HEARTBEAT_INTERVAL_MILLIS = Long.getLong(
            "eureka2.registration.heartbeat.intervalMillis",
            HeartBeatConnection.DEFAULT_HEARTBEAT_INTERVAL_MILLIS
    );

    private final EurekaServerRegistry<InstanceInfo> registry;
    private final EvictionQueue evictionQueue;
    private final WriteServerMetricFactory metricFactory;

    @Inject
    public TcpRegistrationHandler(EurekaServerRegistry registry, EvictionQueue evictionQueue, WriteServerMetricFactory metricFactory) {
        this.registry = registry;
        this.evictionQueue = evictionQueue;
        this.metricFactory = metricFactory;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        MessageConnection broker = new HeartBeatConnection(
                new BaseMessageConnection("registration", connection, metricFactory.getRegistrationConnectionMetrics()),
                HEARTBEAT_INTERVAL_MILLIS, 3,
                Schedulers.computation()
        );
        final ServerChannelFactory service = new ServerChannelFactoryImpl(registry, null, evictionQueue, broker, metricFactory);
        return service.newRegistrationChannel()
                .asLifecycleObservable(); // Since this is a discovery handler which only handles interest subscriptions,
        // the channel is created on connection accept.
    }
}
