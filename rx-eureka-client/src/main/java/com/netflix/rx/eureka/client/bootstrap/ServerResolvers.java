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

package com.netflix.rx.eureka.client.bootstrap;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.rx.eureka.client.ServerResolver;
import com.netflix.rx.eureka.client.ServerResolver.Protocol;
import com.netflix.rx.eureka.client.ServerResolver.ProtocolType;

/**
 * {@link ServerResolvers} provides a collection of static factory methods for
 * construction {@link ServerResolver}s of different kinds.
 *
 * @author Tomasz Bak
 */
public final class ServerResolvers {

    private ServerResolvers() {
    }

    public static ServerResolver<InetSocketAddress> forDomainName(String domainName, Protocol... protocols) {
        int idx = domainName.indexOf(':');
        if (idx != -1) {
            throw new IllegalArgumentException("Expected server domain name without port number attached; protocol ports are defined explicitly");
        }
        return new DnsServerResolver(domainName, protocols == null ? null : new HashSet<Protocol>(Arrays.asList(protocols)));
    }

    public static ServerResolver<InetSocketAddress> forDomainName(String domainName, ProtocolType protocolType) {
        int idx = domainName.indexOf(':');
        int port = idx == -1 ? protocolType.defaultPort() : Integer.parseInt(domainName.substring(idx + 1));
        return new DnsServerResolver(domainName, Collections.singleton(new Protocol(port, protocolType)));
    }

    /**
     * Each entry should be in the format <host>[:<port>]. If the port is not given, the
     * default value associated with a provided protocol is used instead.
     */
    public static ServerResolver<InetSocketAddress> fromList(ProtocolType protocolType, String... entries) {
        StaticServerResolver<InetSocketAddress> resolver = new StaticServerResolver<>();
        for (String entry : entries) {
            int cidx = entry.indexOf(':');
            String host;
            int port;
            if (cidx == -1) {
                host = entry;
                port = protocolType.defaultPort();
            } else {
                host = entry.substring(0, cidx);
                port = Integer.parseInt(entry.substring(cidx + 1));
            }
            resolver.addServer(new InetSocketAddress(host, port), Collections.singleton(new Protocol(port, protocolType)));
        }
        return resolver;
    }

    public static ServerResolver<InetSocketAddress> fromList(Set<Protocol> protocols, String... hosts) {
        StaticServerResolver<InetSocketAddress> resolver = new StaticServerResolver<>();
        for (String host : hosts) {
            resolver.addServer(new InetSocketAddress(host, 0), protocols);
        }
        return resolver;
    }
}
