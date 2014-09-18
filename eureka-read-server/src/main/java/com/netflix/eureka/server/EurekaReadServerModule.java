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

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.EurekaClientImpl;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.transport.EurekaTransports.Codec;

import java.net.InetSocketAddress;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerModule extends AbstractModule {

    private static final TypeLiteral<ServerResolver<InetSocketAddress>> SERVER_RESOLVER_TYPE_LITERAL =
            new TypeLiteral<ServerResolver<InetSocketAddress>>() {
            };

    private final ServerResolver<InetSocketAddress> writeServerResolver;
    private final Codec codec;

    public EurekaReadServerModule(ServerResolver<InetSocketAddress> writeServerResolver, Codec codec) {
        this.writeServerResolver = writeServerResolver;
        this.codec = codec;
    }

    @Override
    public void configure() {
        /*
         * Since, the read server connects to the write server for both read and write, there is only one resolver.
         */
        bind(SERVER_RESOLVER_TYPE_LITERAL).annotatedWith(Names.named(EurekaClient.WRITE_SERVER_RESOLVER_NAME))
                                          .toInstance(writeServerResolver);
        bind(SERVER_RESOLVER_TYPE_LITERAL).annotatedWith(Names.named(EurekaClient.READ_SERVER_RESOLVER_NAME))
                                          .toInstance(writeServerResolver);
        bind(Codec.class).toInstance(codec);

        bind(EurekaClient.class).to(EurekaClientImpl.class);
        bind(EurekaRegistry.class).to(EurekaReadServerRegistry.class);
    }
}
