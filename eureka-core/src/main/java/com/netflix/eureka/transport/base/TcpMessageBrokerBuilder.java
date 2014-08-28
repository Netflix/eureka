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

package com.netflix.eureka.transport.base;

import java.net.InetSocketAddress;

import com.netflix.eureka.transport.MessageBroker;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class TcpMessageBrokerBuilder extends AbstractMessageBrokerBuilder<TcpMessageBrokerBuilder> {
    public TcpMessageBrokerBuilder(InetSocketAddress address) {
        super(address);
    }

    public BaseMessageBrokerServer buildServer() {
        final PublishSubject<MessageBroker> brokersSubject = PublishSubject.create();
        RxServer server = RxNetty.newTcpServerBuilder(address.getPort(), new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Object, Object> newConnection) {
                BaseMessageBroker broker = new BaseMessageBroker(newConnection);
                brokersSubject.onNext(broker);
                return broker.lifecycleObservable();
            }
        }).pipelineConfigurator(codecPipeline)
                .enableWireLogging(LogLevel.ERROR).build();

        return new BaseMessageBrokerServer(server, brokersSubject);
    }

    public Observable<MessageBroker> buildClient() {
        return RxNetty.newTcpClientBuilder(address.getHostName(), address.getPort())
                .pipelineConfigurator(codecPipeline)
                .enableWireLogging(LogLevel.ERROR)
                .build()
                .connect()
                .map(new Func1<ObservableConnection, MessageBroker>() {
                    @Override
                    public MessageBroker call(ObservableConnection observableConnection) {
                        return new BaseMessageBroker(observableConnection);
                    }
                });
    }
}
