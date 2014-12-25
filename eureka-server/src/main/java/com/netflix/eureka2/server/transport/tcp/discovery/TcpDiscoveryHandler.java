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

package com.netflix.eureka2.server.transport.tcp.discovery;

import javax.inject.Inject;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.InterestChannelFactory;
import com.netflix.eureka2.server.channel.InterestChannelFactoryImpl;
import com.netflix.eureka2.server.metric.EurekaServerMetricFactory;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.transport.base.HeartBeatConnection;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class TcpDiscoveryHandler implements ConnectionHandler<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(TcpDiscoveryHandler.class);

    // FIXME add an override from sys property for now
    private static final long HEARTBEAT_INTERVAL_MILLIS = Long.getLong(
            "eureka2.discovery.heartbeat.intervalMillis",
            HeartBeatConnection.DEFAULT_HEARTBEAT_INTERVAL_MILLIS
    );

    private final EurekaServerRegistry<InstanceInfo> registry;
    private final EurekaServerMetricFactory metricFactory;

    @Inject
    public TcpDiscoveryHandler(EurekaServerRegistry registry, EurekaServerMetricFactory metricFactory) {
        this.registry = registry;
        this.metricFactory = metricFactory;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        if (logger.isDebugEnabled()) {
            logger.debug("New TCP discovery client connection");
        }
        MessageConnection broker = new HeartBeatConnection(
                new BaseMessageConnection("discovery", connection, metricFactory.getDiscoveryConnectionMetrics()),
                HEARTBEAT_INTERVAL_MILLIS, 3,
                Schedulers.computation()
        );
        final InterestChannelFactory service = new InterestChannelFactoryImpl(registry, broker, metricFactory);
        // Since this is a discovery handler which only handles interest subscriptions,
        // the channel is created on connection accept. We subscribe here for the sake
        // of logging only.
        Observable<Void> lifecycleObservable = service.newInterestChannel().asLifecycleObservable();
        lifecycleObservable.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Connection from client terminated");
            }

            @Override
            public void onError(Throwable e) {
                logger.info("Connection from client terminated with error");
                logger.debug("Connection exception", e);
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });
        return lifecycleObservable;
    }
}
