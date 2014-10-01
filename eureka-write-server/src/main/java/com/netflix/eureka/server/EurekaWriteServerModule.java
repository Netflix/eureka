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

package com.netflix.eureka.server;

import java.net.InetSocketAddress;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.server.audit.AuditedRegistry;
import com.netflix.eureka.server.replication.ReplicationService;
import com.netflix.eureka.transport.EurekaTransports.Codec;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerModule extends AbstractModule {

    private static final TypeLiteral<ServerResolver<InetSocketAddress>> SERVER_RESOLVER_LITERAL = new TypeLiteral<ServerResolver<InetSocketAddress>>() {
    };

    private final LocalInstanceInfoResolver localInstanceInfoResolver;
    private final ServerResolver<InetSocketAddress> peerResolver;
    private final Codec codec;
    private final long reconnectDelayMs;
    private final long heartbeatIntervalMs;

    public EurekaWriteServerModule(LocalInstanceInfoResolver localInstanceInfoResolver, ServerResolver<InetSocketAddress> peerResolver, Codec codec, long reconnectDelayMs, long heartbeatIntervalMs) {
        this.localInstanceInfoResolver = localInstanceInfoResolver;
        this.peerResolver = peerResolver;
        this.codec = codec;
        this.reconnectDelayMs = reconnectDelayMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    @Override
    public void configure() {
        bind(LocalInstanceInfoResolver.class).toInstance(localInstanceInfoResolver);
        bind(Codec.class).toInstance(codec);
        bind(EurekaRegistry.class).to(AuditedRegistry.class);
        bind(SERVER_RESOLVER_LITERAL).annotatedWith(Names.named(ReplicationService.PEER_RESOLVER_TAG)).toInstance(peerResolver);
        bind(Long.class).annotatedWith(Names.named(ReplicationService.RECONNECT_DELAY_TAG)).toInstance(reconnectDelayMs);
        bind(Long.class).annotatedWith(Names.named(ReplicationService.HEART_BEAT_INTERVAL_TAG)).toInstance(heartbeatIntervalMs);
        bind(ReplicationService.class).asEagerSingleton();
    }
}
