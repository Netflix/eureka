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
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.server.RxServer;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class TcpMessageBrokerBuilder<I, O> extends AbstractMessageBrokerBuilder<I, O, TcpMessageBrokerBuilder<I, O>> {
    // We should set it to a reasonable level, so we rather fail
    // than accumulate data indefinitely.
    private static final int MAX_FRAME_LENGTH = 65536;

    public TcpMessageBrokerBuilder(InetSocketAddress address) {
        super(address);
    }

    public BaseMessageBrokerServer<O, I> buildServer() {
        final PublishSubject<MessageBroker<O, I>> brokersSubject = PublishSubject.create();
        RxServer<O, I> server = RxNetty.newTcpServerBuilder(address.getPort(), new ConnectionHandler<O, I>() {
            @Override
            public Observable<Void> handle(ObservableConnection<O, I> newConnection) {
                BaseMessageBroker<O, I> broker = new BaseMessageBroker<O, I>(newConnection);
                brokersSubject.onNext(broker);
                return broker.lifecycleObservable();
            }
        }).pipelineConfigurator(new TcpPipelineConfigurator<O, I>())
                .appendPipelineConfigurator(codecPipeline)
                .enableWireLogging(LogLevel.ERROR).build();

        return new BaseMessageBrokerServer<O, I>(server, brokersSubject);
    }

    public Observable<MessageBroker<I, O>> buildClient() {
        return RxNetty.<O, I>newTcpClientBuilder(address.getHostName(), address.getPort())
                .pipelineConfigurator(new TcpPipelineConfigurator<I, O>())
                .appendPipelineConfigurator(codecPipeline)
                .enableWireLogging(LogLevel.ERROR)
                .build()
                .connect()
                .map(new Func1<ObservableConnection<I, O>, MessageBroker<I, O>>() {
                    @Override
                    public MessageBroker<I, O> call(ObservableConnection<I, O> observableConnection) {
                        return new BaseMessageBroker<I, O>(observableConnection);
                    }
                });
    }

    static class TcpPipelineConfigurator<I, O> implements PipelineConfigurator<I, O> {
        @Override
        public void configureNewPipeline(ChannelPipeline pipeline) {
            pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
            pipeline.addLast(new LengthFieldPrepender(4));
        }
    }
}
