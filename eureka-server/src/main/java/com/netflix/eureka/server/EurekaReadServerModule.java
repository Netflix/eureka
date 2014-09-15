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
import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.inject.EurekaClientProvider;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.registry.EurekaProxyRegistry;
import com.netflix.eureka.transport.EurekaTransports.Codec;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerModule extends AbstractModule {

    private static final TypeLiteral<ServerResolver<InetSocketAddress>> SERVER_RESOLVER_TYPE_LITERAL =
            new TypeLiteral<ServerResolver<InetSocketAddress>>() {
            };

    private final InstanceInfo localInstanceInfo;
    private final ServerResolver<InetSocketAddress> resolver;
    private final Codec codec;

    public EurekaReadServerModule(InstanceInfo localInstanceInfo, ServerResolver<InetSocketAddress> resolver, Codec codec) {
        this.localInstanceInfo = localInstanceInfo;
        this.resolver = resolver;
        this.codec = codec;
    }

    @Override
    public void configure() {
        bind(InstanceInfo.class)
                .annotatedWith(Names.named(EurekaClientProvider.LOCAL_INSTANCE_INFO_TAG))
                .toInstance(localInstanceInfo);
        bind(SERVER_RESOLVER_TYPE_LITERAL).toInstance(resolver);
        bind(Codec.class).toInstance(codec);

        bind(EurekaClient.class).toProvider(EurekaClientProvider.class);
        bind(EurekaRegistry.class).to(EurekaProxyRegistry.class);
    }
}
