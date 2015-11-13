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

import javax.inject.Inject;

import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.transport.base.HeartBeatConnection;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class TcpReplicationHandler implements ConnectionHandler<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(TcpReplicationHandler.class);

    private final WriteServerConfig config;
    private final SelfInfoResolver selfIdentityService;
    private final EurekaRegistry<InstanceInfo> registry;
    private final WriteServerMetricFactory metricFactory;

    @Inject
    public TcpReplicationHandler(WriteServerConfig config,
                                 SelfInfoResolver selfIdentityService,
                                 EurekaRegistry registry,
                                 WriteServerMetricFactory metricFactory) {
        this.config = config;
        this.selfIdentityService = selfIdentityService;
        this.registry = registry;
        this.metricFactory = metricFactory;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        final EurekaConnection broker = new HeartBeatConnection(
                new BaseMessageConnection("replicationReceiver", connection, metricFactory.getReplicationReceiverConnectionMetrics()),
                config.getEurekaTransport().getHeartbeatIntervalMs(), 3,
                Schedulers.computation()
        );

        final ReceiverReplicationChannel channel = doHandle(broker);
        return channel.asLifecycleObservable()
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        channel.start();
                    }
                });
    }

    /* visible for testing */ ReceiverReplicationChannel doHandle(EurekaConnection connection) {
        return new ReceiverReplicationChannel(connection, selfIdentityService, registry, metricFactory.getReplicationChannelMetrics());
    }
}
