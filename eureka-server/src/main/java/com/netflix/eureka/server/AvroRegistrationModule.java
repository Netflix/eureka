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

import com.google.inject.util.Types;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.karyon.transport.MetricEventsListenerFactory;
import com.netflix.karyon.transport.tcp.TcpRxNettyModule;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.server.ServerBuilder;
import io.reactivex.netty.server.ServerMetricsEvent;
import io.reactivex.netty.server.ServerMetricsEvent.EventType;

/**
 * @author Tomasz Bak
 */
public class AvroRegistrationModule extends TcpRxNettyModule<Object, Object> {
    public AvroRegistrationModule() {
        super(Object.class, Object.class, Types.newParameterizedType(RegistrationHandler.class));
    }

    @Override
    public int serverPort() {
        return 8850;
    }

    @Override
    protected ServerBuilder<Object, Object> newServerBuilder(int port, ConnectionHandler<Object, Object> connectionHandler) {
        return EurekaTransports.tcpRegistrationServerBuilder(port, Codec.Avro, connectionHandler);
    }

    @Override
    public MetricEventsListenerFactory<Object, Object, ServerMetricsEvent<EventType>> metricsEventsListenerFactory() {
        return new MetricEventsListenerFactory.TcpMetricEventsListenerFactory<Object, Object>();
    }
}
