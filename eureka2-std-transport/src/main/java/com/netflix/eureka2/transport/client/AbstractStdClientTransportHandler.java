/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.transport.client;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope.ProtocolType;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.transport.ProtocolConverters;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.Observable;

/**
 */
public abstract class AbstractStdClientTransportHandler<I, O> implements ChannelHandler<I, O> {

    private final Server server;
    private final ProtocolType protocolType;
    private final PipelineConfigurator<Object, Object> pipelineConfigurator = new EurekaPipelineConfigurator();

    protected ChannelContext<I, O> channelContext;

    protected AbstractStdClientTransportHandler(Server server, ProtocolType protocolType) {
        this.server = server;
        this.protocolType = protocolType;
    }

    @Override
    public void init(ChannelContext<I, O> channelContext) {
        if (channelContext.hasNext()) {
            throw new IllegalStateException("No more handlers expected in the pipeline");
        }
        this.channelContext = channelContext;
    }

    protected Observable<ObservableConnection<Object, Object>> connect() {
        return RxNetty.createTcpClient(server.getHost(), server.getPort(), pipelineConfigurator).connect();
    }

    protected ProtocolMessageEnvelope asProtocolMessage(ChannelNotification<I> update) {
        return ProtocolConverters.asProtocolEnvelope(protocolType, update);
    }
}
