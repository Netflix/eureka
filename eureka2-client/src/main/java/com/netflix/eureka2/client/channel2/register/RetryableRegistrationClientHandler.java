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

package com.netflix.eureka2.client.channel2.register;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 */
public class RetryableRegistrationClientHandler implements RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RetryableRegistrationClientHandler.class);

    public static final IOException NOT_RECOGNIZED_INSTANCE = new IOException("Registration reply for not recognized instance");

    private final ChannelPipelineFactory<InstanceInfo, InstanceInfo> factory;
    private final long retryDelayMs;
    private final Scheduler scheduler;

    public RetryableRegistrationClientHandler(ChannelPipelineFactory<InstanceInfo, InstanceInfo> factory,
                                              long retryDelayMs,
                                              Scheduler scheduler) {
        this.factory = factory;
        this.retryDelayMs = retryDelayMs;
        this.scheduler = scheduler;
    }

    @Override
    public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        if (channelContext.hasNext()) {
            throw new IllegalStateException("RetryableRegistrationClientHandler must be single handler pipeline");
        }
    }

    @Override
    public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to RetryableRegistrationClientHandler started");

            new RegistrationSession(registrationUpdates, subscriber);
        });
    }

    class RegistrationSession {
        private final Queue<ChannelNotification<InstanceInfo>> unreportedUpdates = new LinkedBlockingQueue<>();
        private final PublishSubject<ChannelNotification<InstanceInfo>> replySubject = PublishSubject.create();
        private final Observable<ChannelNotification<InstanceInfo>> trackedUpdates;

        private final Subscription pipelineSubscription;

        RegistrationSession(Observable<ChannelNotification<InstanceInfo>> registrationUpdates, Subscriber<? super ChannelNotification<InstanceInfo>> subscriber) {
            this.trackedUpdates = registrationUpdates.doOnNext(next -> unreportedUpdates.add(next));

            replySubject.subscribe(subscriber);

            pipelineSubscription = connectPipeline()
                    .doOnError(e -> logger.info("Registration pipeline terminated due to an error", e))
                    .retryWhen(errors -> {
                        return errors.flatMap(e -> Observable
                                .timer(retryDelayMs, TimeUnit.MILLISECONDS, scheduler)
                                .doOnNext(next -> logger.debug("Reconnecting internal pipeline terminated earlier with an error ({})", e.getMessage()))
                        );
                    }, scheduler)
                    .doOnUnsubscribe(() -> logger.debug("RetryableRegistrationClientHandler internal pipeline unsubscribed"))
                    .subscribe();

            subscriber.add(pipelineSubscription);
        }

        private Observable<Void> connectPipeline() {
            return factory.createPipeline().flatMap(newPipeline -> {
                return newPipeline.getFirst()
                        .handle(trackedUpdates)
                        .flatMap(next -> drainUntilCurrentFound(next));
            }).doOnSubscribe(() -> logger.debug("Creating new internal registration pipeline"));
        }

        private Observable<Void> drainUntilCurrentFound(ChannelNotification<InstanceInfo> next) {
            while (!unreportedUpdates.isEmpty()) {
                ChannelNotification<InstanceInfo> head = unreportedUpdates.poll();
                replySubject.onNext(head);
                if (head.getData().equals(next.getData())) {
                    logger.debug("Received reply from internal pipeline matched with tracked instance {}", next.getData().getId());
                    return Observable.empty();
                }
            }
            logger.debug("Received reply from internal pipeline ({}) not matched with any tracked instance", next.getData().getId());
            return Observable.error(NOT_RECOGNIZED_INSTANCE);
        }
    }
}
