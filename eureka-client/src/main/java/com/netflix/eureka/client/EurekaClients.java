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

package com.netflix.eureka.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.ServerResolver.Protocol;
import com.netflix.eureka.client.bootstrap.BufferedServerResolver;
import com.netflix.eureka.client.bootstrap.ServerResolvers;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.client.transport.TransportClients;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

/**
 * A collection of static factory methods to build {@link EurekaClient} instances.
 *
 * @author Tomasz Bak
 */
public final class EurekaClients {

    private EurekaClients() {
    }

    public static <A extends SocketAddress> Observable<EurekaClient> forRegistratonAndDiscovery(final ServerResolver<A> writeClusterResolver,
                                                                                                final ServerResolver<A> readClusterResolver) {

        final BufferedServerResolver<A> bufferedWrite = writeClusterResolver == null ? null : new BufferedServerResolver<>(writeClusterResolver);
        final BufferedServerResolver<A> bufferedRead = readClusterResolver == null ? null : new BufferedServerResolver<>(readClusterResolver);

        return Observable.concat(
                serverResolverReady(bufferedWrite),
                serverResolverReady(bufferedRead),
                Observable.create(new OnSubscribe<EurekaClient>() {
                    @Override
                    public void call(Subscriber<? super EurekaClient> subscriber) {
                        TransportClient writeClient = bufferedWrite == null ? null : TransportClients.newTcpRegistrationClient(inetResolver(bufferedWrite));
                        TransportClient readClient = bufferedRead == null ? null : TransportClients.newTcpDiscoveryClient(inetResolver(bufferedRead));

                        subscriber.onNext(new EurekaClientImpl(writeClient, readClient));
                        subscriber.onCompleted();
                    }
                })
        );
    }

    public static <A extends SocketAddress> Observable<EurekaClient> forRegistration(ServerResolver<A> writeClusterResolver) {
        final BufferedServerResolver<A> bufferedWrite = writeClusterResolver == null ? null : new BufferedServerResolver<>(writeClusterResolver);

        return Observable.concat(
                serverResolverReady(bufferedWrite),
                Observable.create(new OnSubscribe<EurekaClient>() {
                    @Override
                    public void call(Subscriber<? super EurekaClient> subscriber) {
                        TransportClient writeClient = bufferedWrite == null ? null : TransportClients.newTcpRegistrationClient(inetResolver(bufferedWrite));

                        subscriber.onNext(new EurekaClientImpl(writeClient, null));
                        subscriber.onCompleted();
                    }
                })
        );
    }

    public static <A extends SocketAddress> Observable<EurekaClient> forDiscovery(ServerResolver<A> readClusterResolver) {
        final BufferedServerResolver<A> bufferedRead = readClusterResolver == null ? null : new BufferedServerResolver<>(readClusterResolver);

        return Observable.concat(
                serverResolverReady(bufferedRead),
                Observable.create(new OnSubscribe<EurekaClient>() {
                    @Override
                    public void call(Subscriber<? super EurekaClient> subscriber) {
                        TransportClient readClient = bufferedRead == null ? null : TransportClients.newTcpDiscoveryClient(inetResolver(bufferedRead));

                        subscriber.onNext(new EurekaClientImpl(null, readClient));
                        subscriber.onCompleted();
                    }
                })
        );
    }

    public static Observable<EurekaClient> forRegistratonAndDiscovery(String writeClusterDomainName, String readClusterDomainName) {
        ServerResolver<InetSocketAddress> writeClusterResolver = ServerResolvers.forDomainName(Protocol.TcpRegistration, writeClusterDomainName);
        ServerResolver<InetSocketAddress> readClusterResolver = ServerResolvers.forDomainName(Protocol.TcpDiscovery, readClusterDomainName);
        writeClusterResolver.start();
        readClusterResolver.start();
        return forRegistratonAndDiscovery(writeClusterResolver, readClusterResolver);
    }

    public static Observable<EurekaClient> forRegistration(String writeClusterDomainName) {
        ServerResolver<InetSocketAddress> writeClusterResolver = ServerResolvers.forDomainName(Protocol.TcpRegistration, writeClusterDomainName);
        writeClusterResolver.start();
        return forRegistratonAndDiscovery(writeClusterResolver, null);
    }

    public static Observable<EurekaClient> forDiscovery(String readClusterDomainName) {
        ServerResolver<InetSocketAddress> readClusterResolver = ServerResolvers.forDomainName(Protocol.TcpDiscovery, readClusterDomainName);
        readClusterResolver.start();
        return forRegistratonAndDiscovery(null, readClusterResolver);
    }

    /**
     * TODO: for now we expect at least one server in the server pool prior to constructing {@link EurekaClient}.
     */
    private static Observable<EurekaClient> serverResolverReady(BufferedServerResolver<?> serverResolver) {
        if(serverResolver == null) {
            return Observable.empty();
        }
        return BufferedServerResolver.completeOnPoolSize(serverResolver, 1, 30, TimeUnit.SECONDS).cast(EurekaClient.class);

    }

    /**
     * TODO: can we demonstrate SocketAddress other than InetSocketAddress?
     */
    private static ServerResolver<InetSocketAddress> inetResolver(ServerResolver resolver) {
        return resolver;
    }
}
