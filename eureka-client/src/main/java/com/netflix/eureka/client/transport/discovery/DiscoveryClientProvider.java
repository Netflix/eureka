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

package com.netflix.eureka.client.transport.discovery;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Random;

import com.netflix.eureka.client.BootstrapResolver;
import com.netflix.eureka.client.transport.TransportClientProvider;
import com.netflix.eureka.client.transport.discovery.asynchronous.AsyncDiscoveryClient;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public abstract class DiscoveryClientProvider<A extends SocketAddress> implements TransportClientProvider<DiscoveryClient> {

    private final BootstrapResolver<A> resolver;

    // TODO: this is trivial random pickup implementation
    private final Random random = new Random();

    protected DiscoveryClientProvider(BootstrapResolver<A> resolver) {
        this.resolver = resolver;
    }

    @Override
    public Observable<DiscoveryClient> connect() {
        List<A> addresses = resolver.resolveWriteClusterServers();
        return connect(addresses.get(random.nextInt(addresses.size())));
    }

    protected abstract Observable<DiscoveryClient> connect(A address);

    public static DiscoveryClientProvider<InetSocketAddress> tcpClientProvider(BootstrapResolver<InetSocketAddress> resolver, final Codec codec) {
        return new DiscoveryClientProvider<InetSocketAddress>(resolver) {
            @Override
            protected Observable<DiscoveryClient> connect(InetSocketAddress address) {
                return EurekaTransports.tcpDiscoveryClient(address.getHostName(), address.getPort(), codec)
                        .map(new Func1<MessageBroker, DiscoveryClient>() {
                            @Override
                            public DiscoveryClient call(MessageBroker messageBroker) {
                                return new AsyncDiscoveryClient(messageBroker);
                            }
                        });
            }
        };
    }
}
