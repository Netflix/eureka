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

import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.transport.ResolverBasedTransportClient;
import com.netflix.eureka.client.transport.ServerConnection;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;
import rx.functions.Func1;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link TransportClient} implementation for TCP based connections.
 *
 * @author Tomasz Bak
 */
public class TcpDiscoveryClient extends ResolverBasedTransportClient<InetSocketAddress> {

    private final EurekaTransports.Codec codec;

    // TODO: this is trivial random pickup implementation
    private final Random random = new Random();

    public TcpDiscoveryClient(ServerResolver<InetSocketAddress> resolver, EurekaTransports.Codec codec) {
        super(resolver);
        this.codec = codec;
    }

    @Override
    public Observable<ServerConnection> connect() {
        final CopyOnWriteArrayList<InetSocketAddress> servers = this.servers; // So that we can safely do size => get

        InetSocketAddress server = servers.get(random.nextInt(servers.size()));

        return EurekaTransports.tcpDiscoveryClient(server.getHostName(), server.getPort(), codec)
                               .take(1)
                               .map(new Func1<MessageBroker, ServerConnection>() {
                                   @Override
                                   public ServerConnection call(MessageBroker messageBroker) {
                                       return new TcpServerConnection(messageBroker);
                                   }
                               });
    }

    @Override
    public void shutdown() {
    }
}
