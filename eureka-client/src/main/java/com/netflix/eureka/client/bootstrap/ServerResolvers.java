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

package com.netflix.eureka.client.bootstrap;

import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.Protocol;

import java.net.InetSocketAddress;

/**
 * {@link ServerResolvers} provides a collection of static factory methods for
 * construction {@link ServerResolver}s of different kinds.
 *
 * @author Tomasz Bak
 */
public class ServerResolvers {

    public static ServerResolver<InetSocketAddress> forDomainName(Protocol protocol, String domainName) {
        return new DnsServerResolver(protocol, domainName, true);
    }

    /**
     * Each entry should be in the format <host>[:<port>]. If the port is not given, the
     * default value associated with a provided protocol is used instead.
     */
    public static ServerResolver<InetSocketAddress> fromList(Protocol protocol, String... entries) {
        StaticServerResolver<InetSocketAddress> resolver = new StaticServerResolver<>();
        for (String entry : entries) {
            int cidx = entry.indexOf(':');
            String host;
            int port;
            if (cidx == -1) {
                host = entry;
                port = protocol.defaultPort();
            } else {
                host = entry.substring(0, cidx);
                port = Integer.parseInt(entry.substring(cidx + 1));
            }
            resolver.addServer(new InetSocketAddress(host, port), protocol);
        }
        return resolver;
    }
}
