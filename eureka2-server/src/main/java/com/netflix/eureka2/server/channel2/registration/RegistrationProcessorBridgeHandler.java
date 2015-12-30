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

package com.netflix.eureka2.server.channel2.registration;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.channel.ChannelHandlers;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Complex logic in this handler stems from differences between {@link ChannelHandler} interface, and
 * {@link EurekaRegistrationProcessor}. We should be able to replace the latter with {@link ChannelHandler}, which
 * should simplify or even eliminate this bridge.
 */
public class RegistrationProcessorBridgeHandler implements RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationProcessorBridgeHandler.class);

    private static final IOException REGISTRATION_ERROR = new IOException("Registration reply stream terminated with an error");

    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor;

    public RegistrationProcessorBridgeHandler(EurekaRegistrationProcessor registrationProcessor) {
        this.registrationProcessor = registrationProcessor;
    }

    @Override
    public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        if (channelContext.hasNext()) {
            throw new IllegalStateException("RegistrationProcessorBridgeHandler must be the last one in the pipeline");
        }
    }

    @Override
    public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
        return Observable.create(subscriber -> {
            Subject<ChannelNotification<InstanceInfo>, ChannelNotification<InstanceInfo>> reply = new SerializedSubject<>(PublishSubject.create());
            reply.subscribe(subscriber);

            PublishSubject<ChangeNotification<InstanceInfo>> processorInput = PublishSubject.create();
            AtomicReference<InstanceInfo> lastInstanceRef = new AtomicReference<InstanceInfo>();

            Observable<ChannelNotification<InstanceInfo>> trackedUpdates = registrationUpdates
                    .filter(next -> next.getKind() == ChannelNotification.Kind.Data)
                    .doOnNext(next -> {
                        reply.onNext(next);
                        if (next.getKind() == ChannelNotification.Kind.Data) {
                            lastInstanceRef.set(next.getData());
                        }
                    })
                    .doOnUnsubscribe(() -> {
                        logger.debug("Unsubscribing registration input");
                        if (lastInstanceRef.get() != null) {
                            processorInput.onNext(new ChangeNotification<>(ChangeNotification.Kind.Delete, lastInstanceRef.get()));
                            processorInput.onCompleted();
                        }
                    });

            Subscription inputSubscription = trackedUpdates.subscribe(
                    next -> {
                        if (!processorInput.hasObservers()) {
                            Source clientSource = ChannelHandlers.getClientSource(next);
                            String id = null; // Not used in the code
                            registrationProcessor.connect(id, clientSource, processorInput).subscribe(
                                    ignore -> {
                                    },
                                    e -> {
                                        logger.error("Registration reply stream from client " + clientSource + " terminated with an error", e);
                                        reply.onError(REGISTRATION_ERROR);
                                    },
                                    () -> logger.info("Registration reply stream from client {} onCompleted", clientSource)
                            );
                        }
                        processorInput.onNext(new ChangeNotification<>(ChangeNotification.Kind.Add, next.getData()));
                    },
                    e -> {
                        processorInput.onError(e);
                        reply.onError(e);
                    },
                    reply::onCompleted
            );
            subscriber.add(inputSubscription);
        });
    }
}
