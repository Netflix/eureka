/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.server.transport.tcp.replication;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import com.google.inject.Singleton;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.transport.tcp.AbstractTcpServer;
import com.netflix.eureka2.transport.EurekaTransports;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class TcpReplicationServer extends AbstractTcpServer<WriteServerConfig, WriteServerMetricFactory> {

    private static final Logger logger = LoggerFactory.getLogger(TcpReplicationServer.class);

    private final SelfInfoResolver SelfIdentityService;
    private final EvictionQueue evictionQueue;

    @Inject
    public TcpReplicationServer(WriteServerConfig config,
                                SourcedEurekaRegistry eurekaRegistry,
                                SelfInfoResolver SelfIdentityService,
                                EvictionQueue evictionQueue,
                                @Named("replication") MetricEventsListenerFactory servoEventsListenerFactory,
                                WriteServerMetricFactory metricFactory) {
        super(eurekaRegistry, servoEventsListenerFactory, config, metricFactory);
        this.SelfIdentityService = SelfIdentityService;
        this.evictionQueue = evictionQueue;
    }

    @PostConstruct
    public void start() {
        server = RxNetty.newTcpServerBuilder(
                config.getReplicationPort(),
                new TcpReplicationHandler(SelfIdentityService, eurekaRegistry, evictionQueue, metricFactory))
                .pipelineConfigurator(EurekaTransports.replicationPipeline(config.getCodec()))
                .withMetricEventsListenerFactory(servoEventsListenerFactory)
                .build()
                .start();

        logger.info("Starting TCP replication server on port {} with {} encoding...", server.getServerPort(), config.getCodec());
    }
}
