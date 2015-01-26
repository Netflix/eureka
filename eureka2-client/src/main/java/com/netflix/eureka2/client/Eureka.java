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
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.transport.EurekaTransports;
import rx.functions.Func1;

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
     * Creates a new {@link EurekaClientBuilder} using the passed resolver instance for both discovery and
     * registration endpoints. {@link ServerResolver} must point to Eureka write cluster exposing services
     * on a default ports.
     *
     * @param universalResolver A {@link ServerResolver} that is used both for resolving read and write eureka servers.
     *                          For this resolver, a port number if given is ignored, and a standard port numbers
     *                          for discovery and registration endpoints are used.
     *
     * @return A new {@link EurekaClientBuilder}.
     */
    public static EurekaClientBuilder newClientBuilder(final ServerResolver universalResolver) {
        ServerResolver discoveryResolver = ServerResolvers.apply(universalResolver, new Func1<Server, Server>() {
            @Override
            public Server call(Server server) {
                return new Server(server.getHost(), EurekaTransports.DEFAULT_DISCOVERY_PORT);
            }
        });
        ServerResolver registrationResolver = ServerResolvers.apply(universalResolver, new Func1<Server, Server>() {
            @Override
            public Server call(Server server) {
                return new Server(server.getHost(), EurekaTransports.DEFAULT_REGISTRATION_PORT);
            }
        });
        return newClientBuilder(discoveryResolver, registrationResolver);
    }

    /**
     * Creates a new {@link EurekaClientBuilder} using the passed resolver instance, that must point to
     * Eureka write cluster exposing services on a default ports. Eureka read cluster nodes are discovered
     * in write cluster registry using the provided readServerVip parameter.
     * The builder is initialized in canonical setup, where ultimately a client registers to a write server,
     * and subscribes to the registry on a read server.
     *
     * @param universalResolver A {@link ServerResolver} that is used both for resolving read and write eureka servers.
     *                          For this resolver, a port number if given is ignored, and a standard port numbers
     *                          for discovery and registration endpoints are used.
     * @param readServerVip Eureka read server vip, used to discover read servers in Eureka registry.
     *
     * @return A new {@link EurekaClientBuilder}.
     */
    public static EurekaClientBuilder newClientBuilder(final ServerResolver universalResolver, String readServerVip) {
        ServerResolver discoveryResolver = ServerResolvers.apply(universalResolver, new Func1<Server, Server>() {
            @Override
            public Server call(Server server) {
                return new Server(server.getHost(), EurekaTransports.DEFAULT_DISCOVERY_PORT);
            }
        });
        ServerResolver registrationResolver = ServerResolvers.apply(universalResolver, new Func1<Server, Server>() {
            @Override
            public Server call(Server server) {
                return new Server(server.getHost(), EurekaTransports.DEFAULT_REGISTRATION_PORT);
            }
        });
        return newClientBuilder(discoveryResolver, registrationResolver, readServerVip);
    }

    /**
     * Creates a new {@link EurekaClientBuilder} using the passed resolver instance for write, and construct
     * the read resolver from reading write server data.
     *
     * @param writeClusterDiscoveryResolver a {@link ServerResolver} of discovery endpoint on write cluster
     * @param writeClusterRegistrationResolver a {@link ServerResolver} of registration endpoint on write cluster
     * @param readServerVip the vip address for the read cluster
     *
     * @return A new {@link EurekaClientBuilder}.
     */
    public static EurekaClientBuilder newClientBuilder(ServerResolver writeClusterDiscoveryResolver,
                                                       ServerResolver writeClusterRegistrationResolver,
                                                       String readServerVip) {
        ServerResolver readResolver = ServerResolvers.fromWriteServer(writeClusterDiscoveryResolver, readServerVip);

        return EurekaClientBuilder.newBuilder()
                .withReadServerResolver(readResolver)
                .withWriteServerResolver(writeClusterRegistrationResolver);
    }

}
