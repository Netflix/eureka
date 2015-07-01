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

import javax.inject.Inject;
import javax.inject.Named;

import com.google.inject.Provider;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.channel.RegistrationChannelImpl;
import com.netflix.eureka2.server.config.WriteServerConfig;
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

    private final WriteServerConfig config;
    private final Provider<EurekaRegistrationProcessor> registrationProcessor;
    private final WriteServerMetricFactory metricFactory;

    @Inject
    public TcpRegistrationHandler(WriteServerConfig config,
                                  @Named(Names.REGISTRATION) Provider<EurekaRegistrationProcessor> registrationProcessor,
                                  WriteServerMetricFactory metricFactory) {
        this.config = config;
        this.registrationProcessor = registrationProcessor;
        this.metricFactory = metricFactory;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        MessageConnection broker = new HeartBeatConnection(
                new BaseMessageConnection(Names.REGISTRATION, connection, metricFactory.getRegistrationConnectionMetrics()),
                config.getEurekaTransport().getHeartbeatIntervalMs(), 3,
                Schedulers.computation()
        );


        RegistrationChannel registrationChannel =
                new RegistrationChannelImpl(registrationProcessor.get(), broker, metricFactory.getRegistrationChannelMetrics());

        // Since this is a discovery handler which only handles interest subscriptions,
        // the channel is created on connection accept.
        return registrationChannel.asLifecycleObservable();
    }
}
