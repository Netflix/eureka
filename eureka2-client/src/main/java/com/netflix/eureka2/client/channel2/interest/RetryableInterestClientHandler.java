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

package com.netflix.eureka2.client.channel2.interest;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
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
public class RetryableInterestClientHandler implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RetryableInterestClientHandler.class);

    private final ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> factory;
    private final long retryDelayMs;
    private final Scheduler scheduler;

    public RetryableInterestClientHandler(ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> factory,
                                          long retryDelayMs,
                                          Scheduler scheduler) {
        this.factory = factory;
        this.retryDelayMs = retryDelayMs;
        this.scheduler = scheduler;
    }


    @Override
    public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        if (channelContext.hasNext()) {
            throw new IllegalStateException("RetryableInterestClientHandler must be single handler pipeline");
        }
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> interests) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to RetryableInterestClientHandler started");

            new InterestSession(interests, subscriber);
        });
    }

    private class InterestSession {

        private final Observable<ChannelNotification<Interest<InstanceInfo>>> interests;

        private volatile ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> pipeline;

        private final Subscription pipelineSubscription;

        InterestSession(Observable<ChannelNotification<Interest<InstanceInfo>>> interests,
                        Subscriber<? super ChannelNotification<ChangeNotification<InstanceInfo>>> subscriber) {
            this.interests = interests;
            pipelineSubscription = connectPipeline()
                    .doOnError(e -> logger.info("Interest pipeline terminated due to an error", e))
                    .retryWhen(errors -> {
                        return errors.flatMap(e -> Observable
                                .timer(retryDelayMs, TimeUnit.MILLISECONDS, scheduler)
                                .doOnNext(next -> logger.debug("Reconnecting internal pipeline terminated earlier with an error ({})", e.getMessage()))
                        );
                    }, scheduler)
                    .subscribe(
                            next -> subscriber.onNext(next),
                            e -> subscriber.onError(e),
                            () -> subscriber.onCompleted()
                    );
            subscriber.add(pipelineSubscription);
        }

        private Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> connectPipeline() {
            return factory.createPipeline().flatMap(newPipeline -> {
                pipeline = newPipeline;
                return newPipeline.getFirst().handle(interests);
            }).doOnSubscribe(() -> logger.debug("Creating new internal interest pipeline"));
        }
    }
}
