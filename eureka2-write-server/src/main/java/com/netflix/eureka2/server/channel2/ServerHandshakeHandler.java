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

package com.netflix.eureka2.server.channel2;

import com.netflix.eureka2.channel2.ChannelHandlers;
import com.netflix.eureka2.channel2.SourceIdGenerator;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.model.ClientHello;
import com.netflix.eureka2.spi.model.ServerHello;
import com.netflix.eureka2.spi.model.TransportModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.observers.SerializedSubscriber;

import java.util.concurrent.atomic.AtomicReference;

/**
 */
public class ServerHandshakeHandler<I, O> implements ChannelHandler<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandshakeHandler.class);

    private final ServerHello serverHello;
    private final SourceIdGenerator idGenerator;

    private ChannelContext<I, O> channelContext;

    public ServerHandshakeHandler(Source serverSource, SourceIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
        this.serverHello = TransportModel.getDefaultModel().newServerHello(serverSource);
    }

    @Override
    public void init(ChannelContext<I, O> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("ServerHandshakeHandler cannot be last handler in the pipeline");
        }
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<O>> handle(Observable<ChannelNotification<I>> inputStream) {
        return Observable.create(subscriber -> {
            AtomicReference<Source> clientSource = new AtomicReference<Source>();

            SerializedSubscriber<ChannelNotification<O>> serializedSubscriber = new SerializedSubscriber<ChannelNotification<O>>(subscriber);

            Observable<ChannelNotification<I>> interceptedInput = inputStream.flatMap(inputNotification -> {
                if (inputNotification.getKind() != ChannelNotification.Kind.Hello) {
                    ChannelNotification<I> sourcedNotification = ChannelHandlers.setClientSource(inputNotification, clientSource.get());
                    return Observable.just(sourcedNotification);
                }
                ClientHello clientHello = inputNotification.getHello();
                logger.info("Received client hello {}", clientHello);
                clientSource.set(idGenerator.nextOf(clientHello.getClientSource()));

                serializedSubscriber.onNext(ChannelNotification.newHello(serverHello));
                return Observable.empty();
            });

            channelContext.next().handle(interceptedInput).subscribe(serializedSubscriber);
            serializedSubscriber.add(subscriber);
        });
    }
}
