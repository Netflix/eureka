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

package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.client.transport.TransportClient;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.transport.base.HeartBeatConnection;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.net.InetSocketAddress;

/**
 * {@link ReplicationTransportClient} is always associated with single write server.
 *
 * @author Tomasz Bak
 */
public class ReplicationTransportClient implements TransportClient {

    private final RxClient<Object, Object> rxClient;
    private final MessageConnectionMetrics metrics;

    public ReplicationTransportClient(InetSocketAddress address, Codec codec, MessageConnectionMetrics metrics) {
        this.metrics = metrics;
        this.rxClient = RxNetty.newTcpClientBuilder(address.getHostName(), address.getPort())
                .pipelineConfigurator(EurekaTransports.replicationPipeline(codec))
                .build();
    }

    @Override
    public Observable<MessageConnection> connect() {
        return rxClient.connect()
                .take(1)
                .map(new Func1<ObservableConnection<Object, Object>, MessageConnection>() {
                    @Override
                    public MessageConnection call(ObservableConnection<Object, Object> connection) {
                        return new HeartBeatConnection(new BaseMessageConnection("replicationClient", connection, metrics), 30000, 3, Schedulers.computation());
                    }
                });
    }

    @Override
    public void shutdown() {
        rxClient.shutdown();
    }
}
