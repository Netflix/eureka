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

package com.netflix.eureka2.server.transport;

import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.inject.Provider;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.channel.InputChangeNotificationSourcingHandler;
import com.netflix.eureka2.channel.LoggingChannelHandler;
import com.netflix.eureka2.channel.LoggingChannelHandler.LogLevel;
import com.netflix.eureka2.channel.SourceIdGenerator;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.channel2.ServerHandshakeHandler;
import com.netflix.eureka2.server.channel2.ServerHeartbeatHandler;
import com.netflix.eureka2.server.channel2.interest.InterestMultiplexerBridgeHandler;
import com.netflix.eureka2.server.channel2.registration.RegistrationProcessorBridgeHandler;
import com.netflix.eureka2.server.channel2.replication.ReceiverReplicationHandler;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.service.selfinfo.ConfigSelfInfoResolver;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory.ServerContext;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;

/**
 */
public class EurekaTransportServer {

    private static final Logger logger = LoggerFactory.getLogger(EurekaTransportServer.class);

    private static final long SERVER_STARTUP_TIMEOUT_MS = 30 * 1000;

    protected final CompletableFuture<ServerContext> serverContext = new CompletableFuture<>();
    private final Provider<EurekaRegistrationProcessor> registrationProcessor;
    private final EurekaRegistry<InstanceInfo> registry;
    protected final EurekaRegistryView<InstanceInfo> registryView;
    private final EurekaServerTransportFactory transportFactory;
    protected final EurekaServerTransportConfig config;
    protected final Scheduler scheduler;
    protected final String serverName;
    protected final Source serverSource;

    public EurekaTransportServer(EurekaServerTransportFactory transportFactory,
                                 EurekaServerTransportConfig config,
                                 @Named(Names.REGISTRATION) Provider<EurekaRegistrationProcessor> registrationProcessor,
                                 @Named(Names.REGISTRATION) MetricEventsListenerFactory servoEventsListenerFactory,
                                 EurekaRegistry registry,
                                 EurekaRegistryView registryView,
                                 EurekaInstanceInfoConfig instanceInfoConfig,
                                 Scheduler scheduler) {
        this.transportFactory = transportFactory;
        this.config = config;
        this.registrationProcessor = registrationProcessor;
        this.registry = registry;
        this.registryView = registryView;
        this.scheduler = scheduler;

        // FIXME This is very akward way to get own id, to be able to initialize transport
        this.serverName = ConfigSelfInfoResolver.getFixedSelfInfo(instanceInfoConfig).toBlocking().first().build().getId();
        this.serverSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, serverName);

        if (registrationProcessor != null) {
            connectWrite();
        } else {
            connectRead();
        }
    }

    private void connectWrite() {
        transportFactory.connect(
                config.getRegistrationPort(),
                createRegistrationPipelineFactory(),
                createInterestPipelineFactory(),
                createReplicationPipelineFactory()
        ).subscribe(
                next -> serverContext.complete(next),
                e -> logger.error("EurekaTransportServer connect error", e)
        );
    }

    private void connectRead() {
        transportFactory.connect(
                config.getRegistrationPort(),
                null,
                createInterestPipelineFactory(),
                null
        ).subscribe(
                next -> serverContext.complete(next),
                e -> {
                    logger.error("EurekaTransportServer connect error");
                    serverContext.completeExceptionally(e);
                }
        );
    }

    private ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> createReplicationPipelineFactory() {
        SourceIdGenerator idGenerator = new SourceIdGenerator();
        return new ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void>() {
            @Override
            public Observable<ChannelPipeline<ChangeNotification<InstanceInfo>, Void>> createPipeline() {
                return Observable.create(subscriber -> {
                    subscriber.onNext(new ChannelPipeline<>("replicationServer@" + serverName,
                            new LoggingChannelHandler<ChangeNotification<InstanceInfo>, Void>(LoggingChannelHandler.LogLevel.INFO),
                            new ServerHeartbeatHandler<ChangeNotification<InstanceInfo>, Void>(config.getHeartbeatIntervalMs() * 3, scheduler),
                            new ServerHandshakeHandler<ChangeNotification<InstanceInfo>, Void>(TransportModel.getDefaultModel().newReplicationServerHello(serverSource), idGenerator),
                            new InputChangeNotificationSourcingHandler<InstanceInfo, Void>(),
                            new ReceiverReplicationHandler(registry)
                    ));
                    subscriber.onCompleted();
                });
            }
        };
    }

    private ChannelPipelineFactory<InstanceInfo, InstanceInfo> createRegistrationPipelineFactory() {
        SourceIdGenerator idGenerator = new SourceIdGenerator();
        return new ChannelPipelineFactory<InstanceInfo, InstanceInfo>() {
            @Override
            public Observable<ChannelPipeline<InstanceInfo, InstanceInfo>> createPipeline() {
                return Observable.create(subscriber -> {
                    subscriber.onNext(new ChannelPipeline<>("registrationServer@" + serverName,
                            new LoggingChannelHandler<InstanceInfo, InstanceInfo>(LoggingChannelHandler.LogLevel.INFO),
                            new ServerHeartbeatHandler<InstanceInfo, InstanceInfo>(config.getHeartbeatIntervalMs() * 3, scheduler),
                            new ServerHandshakeHandler<InstanceInfo, InstanceInfo>(TransportModel.getDefaultModel().newServerHello(serverSource), idGenerator),
                            new RegistrationProcessorBridgeHandler(registrationProcessor.get())
                    ));
                    subscriber.onCompleted();
                });
            }
        };
    }

    protected ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> createInterestPipelineFactory() {
        SourceIdGenerator idGenerator = new SourceIdGenerator();
        return new ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> createPipeline() {
                return Observable.create(subscriber -> {
                    subscriber.onNext(new ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>("interestServer@" + serverName,
                            new LoggingChannelHandler(LogLevel.INFO),
                            new ServerHeartbeatHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>(config.getHeartbeatIntervalMs() * 3, scheduler),
                            new ServerHandshakeHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>(TransportModel.getDefaultModel().newServerHello(serverSource), idGenerator),
                            new InterestMultiplexerBridgeHandler(registryView)
                    ));
                    subscriber.onCompleted();
                });
            }
        };
    }

    public int getServerPort() {
        try {
            return serverContext.get(SERVER_STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS).getPort();
        } catch (Exception e) {
            throw new IllegalStateException("Server not ready", e);
        }
    }
}
