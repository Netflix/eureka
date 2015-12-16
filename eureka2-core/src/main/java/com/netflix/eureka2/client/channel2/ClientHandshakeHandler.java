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

package com.netflix.eureka2.client.channel2;

import com.netflix.eureka2.channel2.ChannelHandlers;
import com.netflix.eureka2.channel2.SourceIdGenerator;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.model.ServerHello;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

import java.util.concurrent.atomic.AtomicReference;

/**
 */
public abstract class ClientHandshakeHandler<I, O> implements ChannelHandler<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandshakeHandler.class);

    protected static final IllegalStateException UNEXPECTED_HANDSHAKE_REPLY = new IllegalStateException("Unexpected handshake reply");
    protected static final IllegalStateException DATA_BEFORE_HANDSHAKE_REPLY = new IllegalStateException("Data before handshake reply");

    private final SourceIdGenerator idGenerator;

    protected ChannelContext<I, O> channelContext;

    protected ClientHandshakeHandler(SourceIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public void init(ChannelContext<I, O> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("Expected next element in the pipeline");
        }
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<O>> handle(Observable<ChannelNotification<I>> inputStream) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to ClientHandshakeHandler started");

            AtomicReference<Source> serverSourceRef = new AtomicReference<>();
            channelContext.next().handle(Observable.just(createClientHello()).concatWith(inputStream))
                    .flatMap(handshakeVerifier(serverSourceRef))
                    .doOnUnsubscribe(() -> logger.debug("Unsubscribing from ClientHandshakeHandler"))
                    .subscribe(subscriber);
        });
    }

    protected abstract ChannelNotification<I> createClientHello();

    protected Func1<ChannelNotification<O>, Observable<? extends ChannelNotification<O>>> handshakeVerifier(AtomicReference<Source> serverSourceRef) {
        return replyNotification -> {
            if (replyNotification.getKind() == ChannelNotification.Kind.Hello) {
                ServerHello serverHello = replyNotification.getHello();
                Source serverSource = idGenerator.nextOf(serverHello.getServerSource());
                if (serverSourceRef.getAndSet(serverSource) == null) {
                    return Observable.empty();
                }
                logger.error("Unexpected, excessive handshake reply from server {}", (Object) replyNotification.getHello());
                return Observable.error(UNEXPECTED_HANDSHAKE_REPLY);
            }
            if (replyNotification.getKind() == ChannelNotification.Kind.Data && serverSourceRef.get() == null) {
                logger.error("Data sent from server before handshake has completed");
                return Observable.error(DATA_BEFORE_HANDSHAKE_REPLY);
            }
            return Observable.just(ChannelHandlers.setServerSource(replyNotification, serverSourceRef.get()));
        };
    }
}
