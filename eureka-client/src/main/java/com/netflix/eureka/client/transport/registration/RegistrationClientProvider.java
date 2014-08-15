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

package com.netflix.eureka.client.transport.registration;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Random;

import com.netflix.eureka.client.BootstrapResolver;
import com.netflix.eureka.client.transport.TransportClientProvider;
import com.netflix.eureka.client.transport.registration.asynchronous.AsyncRegistrationClient;
import com.netflix.eureka.client.transport.registration.http.HttpRegistrationClient;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.eureka.transport.MessageBroker;
import io.netty.buffer.ByteBuf;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public abstract class RegistrationClientProvider<A extends SocketAddress> implements TransportClientProvider<RegistrationClient> {

    public static final String BASE_URI = "/discovery";

    private final BootstrapResolver<A> resolver;

    // TODO: this is trivial random pickup implementation
    private final Random random = new Random();

    protected RegistrationClientProvider(BootstrapResolver<A> resolver) {
        this.resolver = resolver;
    }

    @Override
    public Observable<RegistrationClient> connect() {
        List<A> addresses = resolver.resolveWriteClusterServers();
        return connect(addresses.get(random.nextInt(addresses.size())));
    }

    protected abstract Observable<RegistrationClient> connect(A address);

    public static RegistrationClientProvider<InetSocketAddress> tcpClientProvider(BootstrapResolver<InetSocketAddress> resolver, final Codec codec) {
        return new RegistrationClientProvider<InetSocketAddress>(resolver) {
            @Override
            protected Observable<RegistrationClient> connect(InetSocketAddress address) {
                return EurekaTransports.tcpRegistrationClient(address.getHostName(), address.getPort(), codec)
                        .map(new Func1<MessageBroker, RegistrationClient>() {
                            @Override
                            public RegistrationClient call(MessageBroker messageBroker) {
                                return new AsyncRegistrationClient(messageBroker);
                            }
                        });
            }
        };
    }

    public static RegistrationClientProvider<InetSocketAddress> httpClientProvider(BootstrapResolver<InetSocketAddress> resolver) {
        return new RegistrationClientProvider<InetSocketAddress>(resolver) {
            @Override
            protected Observable<RegistrationClient> connect(InetSocketAddress address) {
                HttpClient<ByteBuf, ByteBuf> httpClient = RxNetty.<ByteBuf, ByteBuf>newHttpClientBuilder(address.getHostName(), address.getPort())
                        .enableWireLogging(LogLevel.ERROR)
                        .build();
                return Observable.<RegistrationClient>just(new HttpRegistrationClient(BASE_URI, httpClient));
            }
        };
    }
}
