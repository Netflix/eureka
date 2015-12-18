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

package com.netflix.eureka2.channel2.client;

import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
public class ClientHeartbeatHandler<I, O> implements ChannelHandler<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHeartbeatHandler.class);

    private static final Throwable HEARTBEAT_TIMEOUT = new IOException("Heartbeat timeout");

    private final ChannelNotification<I> heartbeat = ChannelNotification.newHeartbeat();

    private final long heartbeatIntervalMs;
    private final long heartbeatTimeoutMs;
    private final Scheduler scheduler;

    private ChannelContext<I, O> channelContext;

    public ClientHeartbeatHandler(long heartbeatIntervalMs, Scheduler scheduler) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatTimeoutMs = heartbeatIntervalMs * 3;  // FIXME make heartbeat timeout configurable
        this.scheduler = scheduler;
    }

    @Override
    public void init(ChannelContext<I, O> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("Expected next handler in the pipeline");
        }
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<O>> handle(Observable<ChannelNotification<I>> inputStream) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to ClientHeartbeatHandler started");

            AtomicLong lastHeartbeatReply = new AtomicLong(-1);

            Subscription subscription = channelContext.next()
                    .handle(
                            inputStream.mergeWith(
                                    Observable.interval(heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS, scheduler)
                                            .flatMap(tick -> {
                                                if (isLate(lastHeartbeatReply)) {
                                                    logger.debug("No heartbeat reply from server received in {}ms", heartbeatTimeoutMs);
                                                    return Observable.error(HEARTBEAT_TIMEOUT);
                                                }
                                                logger.debug("Sending heartbeat to the server");
                                                return Observable.just(heartbeat);
                                            })
                            )
                    )
                    .doOnUnsubscribe(() -> logger.debug("Unsubscribing from ClientHeartbeatHandler"))
                    .subscribe(
                            next -> {
                                if (next.getKind() == ChannelNotification.Kind.Heartbeat) {
                                    logger.debug("Heartbeat reply from server received");
                                    lastHeartbeatReply.set(scheduler.now());
                                } else {
                                    subscriber.onNext(next);
                                }
                            },
                            e -> {
                                logger.debug("Subscription to ClientHeartbeatHandler completed with an error ({})", e.getMessage());
                                subscriber.onError(e);
                            },
                            () -> {
                                logger.debug("Subscription to ClientHeartbeatHandler onCompleted");
                                subscriber.onCompleted();
                            }
                    );
            subscriber.add(subscription);
        });
    }

    private boolean isLate(AtomicLong lastHeartbeatReply) {
        if (lastHeartbeatReply.get() < 0) {
            lastHeartbeatReply.set(scheduler.now());
            return false;
        }
        long delay = scheduler.now() - lastHeartbeatReply.get();
        return delay > heartbeatTimeoutMs;
    }
}
