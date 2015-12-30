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

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.channel2.LoggingChannelHandler;
import com.netflix.eureka2.channel2.OutputChangeNotificationSourcingHandler;
import com.netflix.eureka2.channel2.SourceIdGenerator;
import com.netflix.eureka2.channel2.client.ClientHeartbeatHandler;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.channel2.interest.InterestClientHandshakeHandler;
import com.netflix.eureka2.client.channel2.interest.InterestLoopDetectorHandler;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.subjects.PublishSubject;

/**
 */
public abstract class AbstractInterestClient2 implements EurekaInterestClient {

    /**
     * Create a pipeline factory for one-time, single server connection. This pipeline is discarded, and recreated
     * on each new client connection.
     */
    protected ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> createPipelineFactory(
            final Source clientSource,
            final ServerResolver serverResolver,
            final EurekaClientTransportFactory transportFactory,
            final EurekaTransportConfig transportConfig,
            final Scheduler scheduler) {
        SourceIdGenerator idGenerator = new SourceIdGenerator();
        return new ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> createPipeline() {
                return serverResolver.resolve().map(server -> {
                            String pipelineId = createPipelineId(clientSource, server);
                            return new ChannelPipeline<>(pipelineId,
                                    new OutputChangeNotificationSourcingHandler(),
                                    new InterestClientHandshakeHandler(clientSource, idGenerator),
                                    new ClientHeartbeatHandler(transportConfig.getHeartbeatIntervalMs(), scheduler),
                                    new LoggingChannelHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>(LoggingChannelHandler.LogLevel.INFO),
                                    transportFactory.newInterestTransport(server)
                            );
                        }
                );
            }

        };
    }

    /**
     * Create a pipeline factory for one-time, single server connection. This pipeline is discarded, and recreated
     * on each new client connection.
     */
    protected ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> createPipelineWithLoopDetectorFactory(
            final Source clientSource,
            final ServerResolver serverResolver,
            final EurekaClientTransportFactory transportFactory,
            final EurekaTransportConfig transportConfig,
            final Scheduler scheduler) {
        SourceIdGenerator idGenerator = new SourceIdGenerator();
        return new ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> createPipeline() {
                return serverResolver.resolve().map(server -> {
                            String pipelineId = createPipelineId(clientSource, server);
                            return new ChannelPipeline<>(pipelineId,
                                    new OutputChangeNotificationSourcingHandler(),
                                    new InterestClientHandshakeHandler(clientSource, idGenerator),
                                    new InterestLoopDetectorHandler(clientSource),
                                    new ClientHeartbeatHandler(transportConfig.getHeartbeatIntervalMs(), scheduler),
                                    new LoggingChannelHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>(LoggingChannelHandler.LogLevel.INFO),
                                    transportFactory.newInterestTransport(server)
                            );
                        }
                );
            }
        };
    }

    /**
     * Subscribe to the retryable pipeline, and connect updates to Eureka registry.
     */
    protected Subscription connectUpdatesToRegistry(ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> retryablePipeline,
                                                    EurekaRegistry eurekaRegistry,
                                                    Observable<ChannelNotification<Interest<InstanceInfo>>> interestNotifications) {
        PublishSubject<ChangeNotification<InstanceInfo>> registryUpdates = PublishSubject.create();
        AtomicReference<Subscription> registrySubscriptionRef = new AtomicReference<>();
        AtomicReference<Source> lastSourceRef = new AtomicReference<>();

        return retryablePipeline.getFirst()
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

    private static String createPipelineId(Source clientSource, Server server) {
        return "interest[client=" + clientSource.getName() + ",server=" + server.getHost() + ':' + server.getPort() + ']';
    }
}
