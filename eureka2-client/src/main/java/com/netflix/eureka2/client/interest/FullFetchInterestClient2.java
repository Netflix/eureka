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

import com.netflix.eureka2.client.channel2.interest.DisconnectingOnEmptyInterestHandler;
import com.netflix.eureka2.client.channel2.interest.RetryableInterestClientHandler;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.health.AbstractHealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.health.SubsystemDescriptor;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;

import java.util.concurrent.atomic.AtomicInteger;

/**
 */
public class FullFetchInterestClient2 extends AbstractInterestClient2 implements HealthStatusProvider<FullFetchInterestClient2> {

    private static final Logger logger = LoggerFactory.getLogger(FullFetchInterestClient2.class);

    private static final SubsystemDescriptor<FullFetchInterestClient2> DESCRIPTOR = new SubsystemDescriptor<>(
            FullFetchInterestClient2.class,
            "Read Server full fetch InterestClient",
            "Source of registry data for Eureka read server clients."
    );

    private final FullFetchInterestClientHealth2 healthProvider;
    private final EurekaRegistry<InstanceInfo> eurekaRegistry;

    private final Subscription registryUpdateSubscription;
    private final Subscription bootstrapSubscription;

    public FullFetchInterestClient2(Source clientSource,
                                    ServerResolver serverResolver,
                                    EurekaClientTransportFactory transportFactory,
                                    EurekaTransportConfig transportConfig,
                                    EurekaRegistry<InstanceInfo> eurekaRegistry,
                                    long retryDelayMs,
                                    Scheduler scheduler) {
        this.healthProvider = new FullFetchInterestClientHealth2();

        this.eurekaRegistry = eurekaRegistry;
        ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> transportPipelineFactory =
                createPipelineWithLoopDetectorFactory(clientSource, serverResolver, transportFactory, transportConfig, scheduler);

        ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> retryablePipeline = new ChannelPipeline<>("readServerInterestClient@" + clientSource.getName(),
                new DisconnectingOnEmptyInterestHandler(),
                new RetryableInterestClientHandler(transportPipelineFactory, retryDelayMs, scheduler)
        );

        Observable<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = Observable.just(
                ChannelNotification.newData(Interests.forFullRegistry())
        ).concatWith(Observable.never());

        this.registryUpdateSubscription = connectUpdatesToRegistry(retryablePipeline, eurekaRegistry, interestNotifications);
        this.bootstrapSubscription = bootstrapUploadSubscribe();
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return eurekaRegistry.forInterest(Interests.forFullRegistry());
    }

    @Override
    public void shutdown() {
        healthProvider.moveHealthTo(InstanceInfo.Status.DOWN);
        registryUpdateSubscription.unsubscribe();
        bootstrapSubscription.unsubscribe();
    }

    @Override
    public Observable<HealthStatusUpdate<FullFetchInterestClient2>> healthStatus() {
        return healthProvider.healthStatus();
    }

    /**
     * Eureka Read server registry is ready when the initial batch of data is uploaded from the server.
     */
    private Subscription bootstrapUploadSubscribe() {
        final AtomicInteger boostrapCount = new AtomicInteger(0);
        return forInterest(Interests.forFullRegistry()).takeWhile(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> notification) {
                boostrapCount.incrementAndGet();
                return notification.getKind() != ChangeNotification.Kind.BufferSentinel;
            }
        }).doOnCompleted(new Action0() {
            @Override
            public void call() {
                healthProvider.moveHealthTo(InstanceInfo.Status.UP);
                logger.info("Initial bootstrap completed. Bootstrapped with {} instances", boostrapCount.get());
            }
        }).subscribe();
    }

    public static class FullFetchInterestClientHealth2 extends AbstractHealthStatusProvider<FullFetchInterestClient2> {

        protected FullFetchInterestClientHealth2() {
            super(InstanceInfo.Status.STARTING, DESCRIPTOR);
        }
    }
}
