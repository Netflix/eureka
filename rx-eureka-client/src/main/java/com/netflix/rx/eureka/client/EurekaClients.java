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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import com.netflix.rx.eureka.client.ServerResolver.Protocol;
import com.netflix.rx.eureka.client.ServerResolver.ProtocolType;
import com.netflix.rx.eureka.client.bootstrap.BufferedServerResolver;
import com.netflix.rx.eureka.client.bootstrap.EurekaServerResolver;
import com.netflix.rx.eureka.client.bootstrap.ServerResolvers;
import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import com.netflix.rx.eureka.client.transport.TransportClient;
import com.netflix.rx.eureka.client.transport.TransportClients;
import com.netflix.rx.eureka.transport.EurekaTransports;
import com.netflix.rx.eureka.transport.EurekaTransports.Codec;
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

    public static <A extends SocketAddress> Observable<EurekaClient> forRegistrationAndDiscovery(final ServerResolver<A> writeClusterResolver,
                                                                                                 final ServerResolver<A> readClusterResolver) {
        return forRegistrationAndDiscovery(writeClusterResolver, readClusterResolver, Codec.Avro);
    }

    public static <A extends SocketAddress> Observable<EurekaClient> forRegistrationAndDiscovery(final ServerResolver<A> writeClusterResolver,
                                                                                                 final ServerResolver<A> readClusterResolver,
                                                                                                 final Codec codec) {
        return forRegistrationAndDiscovery(writeClusterResolver, readClusterResolver, codec, EurekaClientMetricFactory.clientMetrics());
    }

    public static <A extends SocketAddress> Observable<EurekaClient> forRegistrationAndDiscovery(final ServerResolver<A> writeClusterResolver,
                                                                                                 final ServerResolver<A> readClusterResolver,
                                                                                                 final Codec codec,
                                                                                                 final EurekaClientMetricFactory metricFactory) {
        final BufferedServerResolver<A> bufferedWrite = writeClusterResolver == null ? null : new BufferedServerResolver<>(writeClusterResolver);
        final BufferedServerResolver<A> bufferedRead = readClusterResolver == null ? null : new BufferedServerResolver<>(readClusterResolver);

        return Observable.concat(
                serverResolverReady(bufferedWrite),
                serverResolverReady(bufferedRead),
                Observable.create(new OnSubscribe<EurekaClient>() {
                    @Override
                    public void call(Subscriber<? super EurekaClient> subscriber) {
                        TransportClient writeClient = bufferedWrite == null ? null : TransportClients.newTcpRegistrationClient(inetResolver(bufferedWrite), codec);
                        TransportClient readClient = bufferedRead == null ? null : TransportClients.newTcpDiscoveryClient(inetResolver(bufferedRead), codec);

                        subscriber.onNext(new EurekaClientImpl(writeClient, readClient, metricFactory.getRegistryMetrics()));
                        subscriber.onCompleted();
                    }
                })
        );
    }

    /**
     * Read cluster information is fetched from the write cluster.
     *
     * TODO: we keep discovery connection open to the write cluster for read cluster info update. We should implement more sophisticated resolver that can switch to read cluster back and forth.
     */
    public static Observable<EurekaClient> forRegistrationAndDiscovery(final ServerResolver<InetSocketAddress> writeClusterResolver, String eurekaReadClusterVip) {
        return forRegistrationAndDiscovery(
                writeClusterResolver,
                EurekaServerResolver.fromVip(writeClusterResolver, eurekaReadClusterVip, new Protocol(EurekaTransports.DEFAULT_DISCOVERY_PORT, ProtocolType.TcpDiscovery))
        );
    }

    /**
     * TODO: right now this does not work, as internally we try to connect to interest channel as well
     */
    public static <A extends SocketAddress> Observable<EurekaClient> forRegistration(ServerResolver<A> writeClusterResolver) {
        final BufferedServerResolver<A> bufferedWrite = writeClusterResolver == null ? null : new BufferedServerResolver<>(writeClusterResolver);

        return Observable.concat(
                serverResolverReady(bufferedWrite),
                Observable.create(new OnSubscribe<EurekaClient>() {
                    @Override
                    public void call(Subscriber<? super EurekaClient> subscriber) {
                        TransportClient writeClient = bufferedWrite == null ? null : TransportClients.newTcpRegistrationClient(inetResolver(bufferedWrite));

                        subscriber.onNext(new EurekaClientImpl(writeClient, null, EurekaClientMetricFactory.clientMetrics().getRegistryMetrics()));
                        subscriber.onCompleted();
                    }
                })
        );
    }

    /**
     * TODO: right now this does not work, as internally we try to connect to registration channel as well
     */
    public static <A extends SocketAddress> Observable<EurekaClient> forDiscovery(ServerResolver<A> readClusterResolver) {
        final BufferedServerResolver<A> bufferedRead = readClusterResolver == null ? null : new BufferedServerResolver<>(readClusterResolver);

        return Observable.concat(
                serverResolverReady(bufferedRead),
                Observable.create(new OnSubscribe<EurekaClient>() {
                    @Override
                    public void call(Subscriber<? super EurekaClient> subscriber) {
                        TransportClient readClient = bufferedRead == null ? null : TransportClients.newTcpDiscoveryClient(inetResolver(bufferedRead));

                        subscriber.onNext(new EurekaClientImpl(null, readClient, EurekaClientMetricFactory.clientMetrics().getRegistryMetrics()));
                        subscriber.onCompleted();
                    }
                })
        );
    }

    public static Observable<EurekaClient> forRegistrationAndDiscovery(String writeClusterDomainName, String readClusterDomainName) {
        ServerResolver<InetSocketAddress> writeClusterResolver = ServerResolvers.forDomainName(writeClusterDomainName, ProtocolType.TcpRegistration);
        ServerResolver<InetSocketAddress> readClusterResolver = ServerResolvers.forDomainName(readClusterDomainName, ProtocolType.TcpDiscovery);
        writeClusterResolver.start();
        readClusterResolver.start();
        return forRegistrationAndDiscovery(writeClusterResolver, readClusterResolver);
    }

    public static Observable<EurekaClient> forRegistration(String writeClusterDomainName) {
        ServerResolver<InetSocketAddress> writeClusterResolver = ServerResolvers.forDomainName(writeClusterDomainName, ProtocolType.TcpRegistration);
        writeClusterResolver.start();
        return forRegistrationAndDiscovery(writeClusterResolver, (ServerResolver<InetSocketAddress>) null);
    }

    public static Observable<EurekaClient> forDiscovery(String readClusterDomainName) {
        ServerResolver<InetSocketAddress> readClusterResolver = ServerResolvers.forDomainName(readClusterDomainName, ProtocolType.TcpDiscovery);
        readClusterResolver.start();
        return forRegistrationAndDiscovery(null, readClusterResolver);
    }

    /**
     * TODO: for now we expect at least one server in the server pool prior to constructing {@link EurekaClient}.
     */
    private static Observable<EurekaClient> serverResolverReady(BufferedServerResolver<?> serverResolver) {
        if (serverResolver == null) {
            return Observable.empty();
        }
        return BufferedServerResolver.completeOnPoolSize(serverResolver, 1, 30, TimeUnit.SECONDS).cast(EurekaClient.class);

    }

    /**
     * TODO: can we demonstrate SocketAddress other than InetSocketAddress?
     */
    @SuppressWarnings("unchecked")
    private static ServerResolver<InetSocketAddress> inetResolver(ServerResolver resolver) {
        return resolver;
    }
}
