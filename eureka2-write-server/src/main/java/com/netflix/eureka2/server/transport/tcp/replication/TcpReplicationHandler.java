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

package com.netflix.eureka2.server.transport.tcp.replication;

import com.google.inject.Inject;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannelFactory;
import com.netflix.eureka2.server.channel.ServerChannelFactory;
import com.netflix.eureka2.server.metric.WriteServerMetricFactory;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.service.SelfInfoResolver;
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
public class TcpReplicationHandler implements ConnectionHandler<Object, Object> {

    private final SelfInfoResolver SelfIdentityService;
    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final EvictionQueue evictionQueue;
    private final WriteServerMetricFactory metricFactory;

    @Inject
    public TcpReplicationHandler(SelfInfoResolver SelfIdentityService,
                                 SourcedEurekaRegistry registry,
                                 EvictionQueue evictionQueue,
                                 WriteServerMetricFactory metricFactory) {
        this.SelfIdentityService = SelfIdentityService;
        this.registry = registry;
        this.evictionQueue = evictionQueue;
        this.metricFactory = metricFactory;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        MessageConnection broker = new HeartBeatConnection(
                new BaseMessageConnection("replication", connection, metricFactory.getReplicationConnectionMetrics()),
                30000, 3,
                Schedulers.computation()
        );
        final ServerChannelFactory<ReceiverReplicationChannel> channelFactory =
                new ReceiverReplicationChannelFactory(registry, broker, SelfIdentityService, evictionQueue, metricFactory);

        return channelFactory.newChannel().asLifecycleObservable();
    }
}
