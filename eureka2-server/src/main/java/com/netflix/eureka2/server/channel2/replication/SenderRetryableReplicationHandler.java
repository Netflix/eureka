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

package com.netflix.eureka2.server.channel2.replication;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;

import java.util.concurrent.TimeUnit;

/**
 */
public class SenderRetryableReplicationHandler implements ReplicationHandler {

    private static final Logger logger = LoggerFactory.getLogger(SenderRetryableReplicationHandler.class);

    private final ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> factory;
    private final long retryDelayMs;
    private final Scheduler scheduler;

    public SenderRetryableReplicationHandler(ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> factory,
                                             long retryDelayMs,
                                             Scheduler scheduler) {
        this.factory = factory;
        this.retryDelayMs = retryDelayMs;
        this.scheduler = scheduler;
    }

    @Override
    public void init(ChannelContext<ChangeNotification<InstanceInfo>, Void> channelContext) {
        if (channelContext.hasNext()) {
            throw new IllegalStateException("SenderRetryableReplicationHandler must be single handler pipeline");
        }
    }

    @Override
    public Observable<ChannelNotification<Void>> handle(Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to SenderRetryableReplicationHandler started");
            new ReplicationSession(replicationUpdates, subscriber);
        });
    }

    private class ReplicationSession {

        private final Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates;
        private final Subscription pipelineSubscription;

        ReplicationSession(Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates,
                           Subscriber<? super ChannelNotification<Void>> subscriber) {
            this.replicationUpdates = replicationUpdates;
            pipelineSubscription = connectPipeline()
                    .doOnError(e -> logger.info("Replication pipeline terminated due to an error", e))
                    .retryWhen(errors -> {
                        return errors.flatMap(e -> {
                            if (e instanceof ReplicationLoopException) {
                                return Observable.error(e);
                            }
                            return Observable
                                    .timer(retryDelayMs, TimeUnit.MILLISECONDS, scheduler)
                                    .doOnNext(next -> logger.debug("Reconnecting internal pipeline terminated earlier with an error ({})", e.getMessage()));
                        });
                    }, scheduler)
                    .subscribe(
                            next -> subscriber.onNext(next),
                            e -> subscriber.onError(e),
                            () -> subscriber.onCompleted()
                    );
            subscriber.add(pipelineSubscription);
        }

        private Observable<ChannelNotification<Void>> connectPipeline() {
            return factory.createPipeline().flatMap(newPipeline -> {
                return newPipeline.getFirst().handle(replicationUpdates);
            }).doOnSubscribe(() -> logger.debug("Creating new internal replication pipeline"));
        }
    }
}
