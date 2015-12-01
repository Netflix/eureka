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
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.observers.SerializedSubscriber;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

import java.util.concurrent.atomic.AtomicReference;

import static com.netflix.eureka2.client.util.InterestUtil.isEmptyInterest;

/**
 * If the effective interest is empty, there is no point in keeping opened connection to the server.
 * This handler dynamically creates and destroys the underlying subscription, depending on the interest set.
 * <p>
 * This could be improved by not disconnecting immediately, but waiting for some configurable amount of time,
 * so clients that constantly subscribe and unsubscribe to interest stream do not generate excessive socket churn.
 */
public class DisconnectingOnEmptyInterestHandler implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DisconnectingOnEmptyInterestHandler.class);

    private ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext;

    @Override
    public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("DisconnectingOnEmptyInterestHandler cannot be the last handler in the pipeline");
        }
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> interests) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to DisconnectingOnEmptyInterestHandler started");

            AtomicReference<InterestSession> sessionRef = new AtomicReference<>();
            SerializedSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> serializedSubscriber = new SerializedSubscriber<>(subscriber);

            Subscription interestSubscription = interests
                    .doOnUnsubscribe(() -> {
                        logger.debug("Upstream interest notification stream un-subscribed");
                        close(sessionRef);
                    })
                    .subscribe(
                            interestUpdate -> {
                                if (interestUpdate.getKind() == ChannelNotification.Kind.Data && isEmptyInterest(interestUpdate.getData())) {
                                    logger.debug("Empty interest update received - unsubscribing from downstream pipeline");
                                    close(sessionRef);
                                } else {
                                    logger.info("Forwarding interest update {}", interestUpdate.getData());
                                    getOrCreate(serializedSubscriber, sessionRef).onNext(interestUpdate);
                                }
                            },
                            e -> {
                                logger.debug("Interest notification stream terminated with an error ({})", e.getMessage());
                                subscriber.onError(e);
                            },
                            () -> {
                                logger.debug("Interest notification stream onCompleted");
                                subscriber.onCompleted();
                            }
                    );
            subscriber.add(interestSubscription);
        });
    }

    private InterestSession getOrCreate(Subscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> subscriber,
                                        AtomicReference<InterestSession> sessionRef) {
        if (sessionRef.get() == null) {
            sessionRef.set(new InterestSession(subscriber));
        }
        return sessionRef.get();
    }

    private void close(AtomicReference<InterestSession> sessionRef) {
        if (sessionRef.get() != null) {
            sessionRef.getAndSet(null).close();
        }
    }

    class InterestSession {

        private final BehaviorSubject<ChannelNotification<Interest<InstanceInfo>>> interestSubject;
        private final Subscription channelSubscription;

        InterestSession(Subscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> subscriber) {
            logger.debug("Subscribing to downstream channel pipeline");
            interestSubject = BehaviorSubject.create();
            channelSubscription = channelContext.next().handle(interestSubject).subscribe(
                    next -> subscriber.onNext(next),
                    e -> {
                        logger.error("ChangeNotification stream disconnected with an error", e);
                        subscriber.onError(e);
                    },
                    () -> {
                        logger.info("ChangeNotification stream disconnected");
                        subscriber.onCompleted();
                    }
            );
        }

        void onNext(ChannelNotification<Interest<InstanceInfo>> interestUpdate) {
            interestSubject.onNext(interestUpdate);
        }

        void close() {
            logger.debug("Unsubscribing from downstream channel pipeline");
            channelSubscription.unsubscribe();
        }
    }
}
