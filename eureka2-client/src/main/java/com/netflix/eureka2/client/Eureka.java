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

package com.netflix.eureka2.client;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.client.resolver.WriteServerResolverSet;

/**
 * TODO: do we still need this entry point or can we just use the builder directly?
 *
 * An entry point for creating instances of {@link EurekaClient}. A {@link EurekaClient} can be created using the
 * following components:
 *
 * <ul>
 * <li>Read Server Resolver: A resolver that is used to resolve instances of read servers, which provides eureka
 * registry information. A server resolver is expressed as {@link ServerResolver}</li>
 * <li>Write Server Resolver: A resolver that is used to resolve instances of read servers, which provides eureka
 * registration semantics. A server resolver is expressed as {@link ServerResolver}</li>
 * <li>Universal Resolver: A resolver that is used to resolve instances of both read and write servers.
 * A server resolver is expressed as {@link ServerResolver}</li>
 * <li>{@link EurekaClientBuilder}: Use an elaborate mechanism to create {@link EurekaClient} instances which can
 * customize all aspects of the client.</li>
 </ul>
 *
 * @author Tomasz Bak
 */
public final class Eureka {

    private Eureka() {}

    /**
     * Creates a new {@link EurekaClientBuilder} using the passed read and write server resolver instances.
     *
     * @param readResolver {@link ServerResolver} for the read servers.
     * @param writeResolver {@link ServerResolver} for the write servers.
     *
     * @return A new {@link EurekaClientBuilder}.
     */
    public static EurekaClientBuilder newClientBuilder(final ServerResolver readResolver,
                                                       final ServerResolver writeResolver) {
        return EurekaClientBuilder.newBuilder()
                .withReadServerResolver(readResolver)
                .withWriteServerResolver(writeResolver);
    }

    /**
     * Creates a new {@link EurekaClientBuilder} using the passed resolver instance for both read and write eureka
     * servers.
     *
     * @param universalResolver A {@link ServerResolver} that is used both for resolving read and write eureka servers.
     *
     * @return A new {@link EurekaClientBuilder}.
     */
    public static EurekaClientBuilder newClientBuilder(final ServerResolver universalResolver) {
        return EurekaClientBuilder.newBuilder()
                .withReadServerResolver(universalResolver)
                .withWriteServerResolver(universalResolver);
    }

    /**
     * Creates a new {@link EurekaClientBuilder} using the passed resolver instance for write, and construct
     * the read resolver from reading write server data.
     *
     * @param writeResolverSet {@link WriteServerResolverSet} for the write servers.
     * @param readServerVip the vip address for the read cluster
     *
     * @return A new {@link EurekaClientBuilder}.
     */
    public static EurekaClientBuilder newClientBuilder(WriteServerResolverSet writeResolverSet, String readServerVip) {
        ServerResolver readResolver = ServerResolvers.fromWriteServer(writeResolverSet.forDiscovery(), readServerVip);

        return EurekaClientBuilder.newBuilder()
                .withReadServerResolver(readResolver)
                .withWriteServerResolver(writeResolverSet.forRegistration());
    }

}
