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

import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.utils.rx.ExtObservable;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
public class ServerHeartbeatHandler<I, O> implements ChannelHandler<I, O> {

    private static final IOException HEARTBEAT_TIMEOUT = new IOException("Heartbeat timeout");

    private final long heartbeatTimeoutMs;
    private final Scheduler scheduler;

    private ChannelContext<I, O> channelContext;

    public ServerHeartbeatHandler(long heartbeatTimeoutMs, Scheduler scheduler) {
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.scheduler = scheduler;
    }

    @Override
    public void init(ChannelContext<I, O> channelContext) {
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<O>> handle(Observable<ChannelNotification<I>> inputStream) {
        return Observable.create(subscriber -> {

            PublishSubject<ChannelNotification<O>> heartbeatReplies = PublishSubject.create();
            AtomicLong lastTimeoutUpdate = new AtomicLong(scheduler.now());

            // Intercept heartbeats from input
            Observable<ChannelNotification<I>> interceptedInput = inputStream.flatMap(inputNotification -> {
                if (inputNotification.getKind() == ChannelNotification.Kind.Heartbeat) {
                    heartbeatReplies.onNext((ChannelNotification<O>) inputNotification); // Send back heartbeat
                    lastTimeoutUpdate.set(scheduler.now());
                    return Observable.empty();
                }
                return Observable.just(inputNotification);
            }).doOnUnsubscribe(() -> heartbeatReplies.onCompleted());

            // Create heartbeat timeout trigger
            Observable<ChannelNotification<O>> timeoutTrigger = Observable.interval(heartbeatTimeoutMs, heartbeatTimeoutMs, TimeUnit.MILLISECONDS, scheduler)
                    .flatMap(tick -> {
                        long delay = scheduler.now() - lastTimeoutUpdate.get();
                        if (delay >= heartbeatTimeoutMs) {
                            return Observable.error(HEARTBEAT_TIMEOUT);
                        }
                        return Observable.empty();
                    });

            Subscription subscription = ExtObservable.mergeWhenAllActive(
                    channelContext.next().handle(interceptedInput).mergeWith(heartbeatReplies),
                    timeoutTrigger
            ).subscribe(subscriber);
            subscriber.add(subscription);
        });
    }
}
