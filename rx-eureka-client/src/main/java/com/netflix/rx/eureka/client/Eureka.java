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

package com.netflix.rx.eureka.client;

import com.netflix.rx.eureka.client.resolver.ServerResolver;

/**
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

    private Eureka() {
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
        return new EurekaClientBuilder(universalResolver, universalResolver);
    }

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
        return new EurekaClientBuilder(readResolver, writeResolver);
    }


    /**
     * Creates a new {@link EurekaClient} with the passed {@link ServerResolver} for both read and write servers. <p/>
     *
     * <i> The created client, does not eagerly connect to the write or read servers and hence can be used also in case
     * where only one of the read or write server interactions are required.</i>
     *
     * @param universalResolver A {@link ServerResolver} that is used both for resolving read and write eureka servers.
     *
     * @return A new {@link EurekaClient}.
     */
    public static EurekaClient newClient(final ServerResolver universalResolver) {
        return newClientBuilder(universalResolver).build();
    }

    /**
     * Creates a new {@link EurekaClient} using the passed read and write server resolver instances.
     *
     * @param readResolver {@link ServerResolver} for the read servers.
     * @param writeResolver {@link ServerResolver} for the write servers.
     *
     * @return A new {@link EurekaClient}.
     */
    public static EurekaClient newClient(final ServerResolver readResolver, final ServerResolver writeResolver) {
        return newClientBuilder(readResolver, writeResolver).build();
    }
}
