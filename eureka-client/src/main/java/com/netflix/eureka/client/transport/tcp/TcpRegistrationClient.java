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

package com.netflix.eureka.client.transport.tcp;

import java.net.InetSocketAddress;

import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.Protocol;
import com.netflix.eureka.client.ServerResolver.ServerEntry;
import com.netflix.eureka.client.transport.ResolverBasedTransportClient;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.transport.EurekaTransports;

/**
 * A {@link TransportClient} implementation for TCP based connections.
 *
 * @author Tomasz Bak
 */
public class TcpRegistrationClient extends ResolverBasedTransportClient<InetSocketAddress> {

    public TcpRegistrationClient(ServerResolver<InetSocketAddress> resolver, EurekaTransports.Codec codec) {
        super(resolver, getClientConfig("tcpRegistrationClient"), EurekaTransports.registrationPipeline(codec));
    }

    @Override
    protected boolean matches(ServerEntry<InetSocketAddress> entry) {
        return (entry.getProtocol() == Protocol.TcpRegistration || entry.getProtocol() == Protocol.Undefined)
                && entry.getServer() instanceof InetSocketAddress;
    }

    @Override
    protected int defaultPort() {
        return Protocol.TcpRegistration.defaultPort();
    }
}
