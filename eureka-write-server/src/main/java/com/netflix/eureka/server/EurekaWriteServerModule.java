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
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.server.audit.AuditedRegistry;
import com.netflix.eureka.transport.EurekaTransports.Codec;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerModule extends AbstractModule {

    @SuppressWarnings("unused")
    private final ServerResolver<InetSocketAddress> peerResolver;
    private final Codec codec;

    public EurekaWriteServerModule(ServerResolver<InetSocketAddress> peerResolver, Codec codec) {
        this.peerResolver = peerResolver;
        this.codec = codec;
    }

    @Override
    public void configure() {
        bind(Codec.class).toInstance(codec);
        bind(EurekaRegistry.class).to(AuditedRegistry.class);
    }
}
