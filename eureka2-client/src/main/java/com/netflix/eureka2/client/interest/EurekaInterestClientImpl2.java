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

package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.channel2.ClientHeartbeatHandler;
import com.netflix.eureka2.channel2.LoggingChannelHandler;
import com.netflix.eureka2.channel2.LoggingChannelHandler.LogLevel;
import com.netflix.eureka2.client.channel2.interest.DeltaMergingInterestClientHandler;
import com.netflix.eureka2.client.channel2.interest.DisconnectingOnEmptyInterestHandler;
import com.netflix.eureka2.client.channel2.interest.InterestClientHandshakeHandler;
import com.netflix.eureka2.client.channel2.interest.RetryableInterestClientHandler;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.utils.functions.RxFunctions;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

import java.util.concurrent.atomic.AtomicReference;

import static com.netflix.eureka2.client.util.InterestUtil.isEmptyInterest;

/**
 */
public class EurekaInterestClientImpl2 implements EurekaInterestClient {

    private final ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> transportPipelineFactory;
    private final EurekaRegistry<InstanceInfo> eurekaRegistry;

    private final InterestTracker interestTracker = new InterestTracker();
    private final ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> retryablePipeline;

    private final Subscription registryUpdateSubscription;

    public EurekaInterestClientImpl2(Source clientSource,
                                     ServerResolver serverResolver,
                                     EurekaClientTransportFactory transportFactory,
                                     EurekaTransportConfig transportConfig,
                                     EurekaRegistry eurekaRegistry,
                                     long retryDelayMs,
                                     Scheduler scheduler) {
        this.eurekaRegistry = eurekaRegistry;
        this.transportPipelineFactory = new ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> createPipeline() {
                return serverResolver.resolve().map(server -> {
                            String pipelineId = "interest[client=" + clientSource.getName() + ",server=" + server.getHost();
                            return new ChannelPipeline<>(pipelineId,
                                    new DeltaMergingInterestClientHandler(),
                                    new InterestClientHandshakeHandler(clientSource),
                                    new ClientHeartbeatHandler(transportConfig.getHeartbeatIntervalMs(), scheduler),
                                    new LoggingChannelHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>(LogLevel.INFO),
                                    transportFactory.newInterestTransport(server)
                            );
                        }
                );
            }
        };

        retryablePipeline = new ChannelPipeline<>("interest",
                new DisconnectingOnEmptyInterestHandler(),
                new RetryableInterestClientHandler(transportPipelineFactory, retryDelayMs, scheduler)
        );

        Observable<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = interestTracker.interestChangeStream()
                .map(interest -> ChannelNotification.newData(interest));

        PublishSubject<ChangeNotification<InstanceInfo>> registryUpdates = PublishSubject.create();
        AtomicReference<Subscription> registrySubscriptionRef = new AtomicReference<>();
        AtomicReference<Source> lastSourceRef = new AtomicReference<>();
        registryUpdateSubscription = retryablePipeline.getFirst()
                .handle(interestNotifications)
                .subscribe(
                        next -> {
                            if (next.getKind() == ChannelNotification.Kind.Data) {
                                Sourced sourced = (Sourced) next.getData();

                                // Disconnect previous updates if source changes
                                if (lastSourceRef.get() != null && !sourced.getSource().equals(lastSourceRef.get())) {
                                    registrySubscriptionRef.getAndSet(null).unsubscribe();
                                    eurekaRegistry.evictAll(Source.matcherFor(lastSourceRef.get()));
                                }

                                // This will be executed each time a new source is encountered
                                if (registrySubscriptionRef.get() == null) {
                                    registrySubscriptionRef.set(
                                            eurekaRegistry.connect(sourced.getSource(), registryUpdates).subscribe()
                                    );
                                    lastSourceRef.set(sourced.getSource());
                                }
                                registryUpdates.onNext(next.getData());
                            }
                        }
                );
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        if (isEmptyInterest(interest)) {
            return Observable.empty();
        }

        Observable<Void> appendInterest = Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                interestTracker.appendInterest(interest);
                subscriber.onCompleted();
            }
        });

        // strip source from the notifications
        // convert bufferstart/bufferends to just a single buffersentinel
        Observable<ChangeNotification<InstanceInfo>> registryStream = eurekaRegistry.forInterest(interest)
                .map(new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification instanceof SourcedChangeNotification) {
                            return ((SourcedChangeNotification) notification).toBaseNotification();
                        } else if (notification instanceof StreamStateNotification) {
                            StreamStateNotification<InstanceInfo> n = (StreamStateNotification) notification;
                            switch (n.getBufferState()) {
                                case BufferEnd:
                                    return ChangeNotification.bufferSentinel();
                                case BufferStart:
                                default:
                                    return null;
                            }
                        } else {
                            return notification;
                        }
                    }
                })
                .doOnError(e -> e.printStackTrace())
                .filter(RxFunctions.filterNullValuesFunc());

        Observable toReturn = appendInterest
                .cast(ChangeNotification.class)
                .mergeWith(registryStream)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        interestTracker.removeInterest(interest);
                    }
                });

        return toReturn;
    }

    @Override
    public void shutdown() {
        registryUpdateSubscription.unsubscribe();
    }
}
