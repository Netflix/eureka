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

package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.channel.LoggingChannelHandler;
import com.netflix.eureka2.channel.LoggingChannelHandler.LogLevel;
import com.netflix.eureka2.channel.client.ClientHeartbeatHandler;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.channel.register.RegistrationClientHandshakeHandler;
import com.netflix.eureka2.client.channel.register.RetryableRegistrationClientHandler;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;

/**
 */
public class EurekaRegistrationClientImpl implements EurekaRegistrationClient {

    private static final Logger logger = LoggerFactory.getLogger(EurekaRegistrationClientImpl.class);

    private final ChannelPipelineFactory<InstanceInfo, InstanceInfo> transportPipelineFactory;
    private final long retryDelayMs;
    private final Scheduler scheduler;

    public EurekaRegistrationClientImpl(Source clientSource,
                                        ServerResolver serverResolver,
                                        EurekaClientTransportFactory transportFactory,
                                        EurekaTransportConfig transportConfig,
                                        long retryDelayMs,
                                        Scheduler scheduler) {
        this.retryDelayMs = retryDelayMs;
        this.scheduler = scheduler;
        this.transportPipelineFactory = new ChannelPipelineFactory<InstanceInfo, InstanceInfo>() {
            @Override
            public Observable<ChannelPipeline<InstanceInfo, InstanceInfo>> createPipeline() {
                return serverResolver.resolve().map(server -> {
                            String pipelineId = createPipelineId(clientSource, server);
                            return new ChannelPipeline<>(pipelineId,
                                    new RegistrationClientHandshakeHandler(),
                                    new ClientHeartbeatHandler<InstanceInfo, InstanceInfo>(transportConfig.getHeartbeatIntervalMs(), scheduler),
                                    new LoggingChannelHandler<InstanceInfo, InstanceInfo>(LogLevel.INFO),
                                    transportFactory.newRegistrationClientTransport(server)
                            );
                        }
                );
            }
        };
    }

    @Override
    public Observable<RegistrationStatus> register(Observable<InstanceInfo> registrant) {
        return Observable.create(subscriber -> {
            ChannelPipeline<InstanceInfo, InstanceInfo> retryablePipeline = new ChannelPipeline<>("registration",
                    new RetryableRegistrationClientHandler(transportPipelineFactory, retryDelayMs, scheduler)
            );

            Subscription subscription = retryablePipeline.getFirst()
                    .handle(registrant.map(instance -> ChannelNotification.newData(instance)))
                    .map(reply -> RegistrationStatus.Registered)
                    .distinct()
                    .doOnNext(subscriber::onNext)
                    .doOnError(subscriber::onError)
                    .doOnCompleted(subscriber::onCompleted)
                    .doOnUnsubscribe(() -> {
                        logger.debug("Unsubscribing registration client");
                    })
                    .subscribe();

            subscriber.add(subscription);
        });
    }

    @Override
    public void shutdown() {
    }


    private static String createPipelineId(Source clientSource, Server server) {
        return "registration[client=" + clientSource.getName() + ",server=" + server.getHost() + ':' + server.getPort() + ']';
    }
}
