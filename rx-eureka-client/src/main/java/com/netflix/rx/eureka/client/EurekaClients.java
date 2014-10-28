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

/**
 * A collection of static factory methods to build {@link EurekaClient} instances.
 *
 * @author Tomasz Bak
 */
public final class EurekaClients {

    private EurekaClients() {
    }

    public static <A extends SocketAddress> EurekaClient forRegistrationAndDiscovery(final ServerResolver<A> writeClusterResolver,
                                                                                     final ServerResolver<A> readClusterResolver) {
        return forRegistrationAndDiscovery(writeClusterResolver, readClusterResolver, Codec.Avro);
    }

    public static EurekaClient forRegistrationAndDiscovery(final ServerResolver<InetSocketAddress> writeClusterResolver, String eurekaReadClusterVip) {
        return forRegistrationAndDiscovery(writeClusterResolver, eurekaReadClusterVip, EurekaClientMetricFactory.clientMetrics());
    }

    /**
     * Read cluster information is fetched from the write cluster.
     *
     * TODO: we keep discovery connection open to the write cluster for read cluster info update. We should implement more sophisticated resolver that can switch to read cluster back and forth.
     */
    public static EurekaClient forRegistrationAndDiscovery(final ServerResolver<InetSocketAddress> writeClusterResolver, String eurekaReadClusterVip, EurekaClientMetricFactory metricFactory) {
        return forRegistrationAndDiscovery(
                writeClusterResolver,
                EurekaServerResolver.fromVip(writeClusterResolver, eurekaReadClusterVip, new Protocol(EurekaTransports.DEFAULT_DISCOVERY_PORT, ProtocolType.TcpDiscovery), metricFactory)
        );
    }

    public static <A extends SocketAddress> EurekaClient forRegistrationAndDiscovery(final ServerResolver<A> writeClusterResolver,
                                                                                     final ServerResolver<A> readClusterResolver,
                                                                                     final Codec codec) {
        return forRegistrationAndDiscovery(writeClusterResolver, readClusterResolver, codec, EurekaClientMetricFactory.clientMetrics());
    }

    public static <A extends SocketAddress> EurekaClient forRegistrationAndDiscovery(final ServerResolver<A> writeClusterResolver,
                                                                                     final ServerResolver<A> readClusterResolver,
                                                                                     final Codec codec,
                                                                                     final EurekaClientMetricFactory metricFactory) {
        final BufferedServerResolver<A> bufferedWrite = writeClusterResolver == null ? null : new BufferedServerResolver<>(writeClusterResolver);
        final BufferedServerResolver<A> bufferedRead = readClusterResolver == null ? null : new BufferedServerResolver<>(readClusterResolver);

        TransportClient writeClient = bufferedWrite == null ? null : TransportClients.newTcpRegistrationClient(inetResolver(bufferedWrite), codec);
        TransportClient readClient = bufferedRead == null ? null : TransportClients.newTcpDiscoveryClient(inetResolver(bufferedRead), codec);

        return new EurekaClientImpl(writeClient, readClient, metricFactory);
    }

    public static EurekaClient forRegistrationAndDiscovery(String writeClusterDomainName, String readClusterDomainName) {
        ServerResolver<InetSocketAddress> writeClusterResolver = ServerResolvers.forDomainName(writeClusterDomainName, ProtocolType.TcpRegistration);
        ServerResolver<InetSocketAddress> readClusterResolver = ServerResolvers.forDomainName(readClusterDomainName, ProtocolType.TcpDiscovery);
        writeClusterResolver.start();
        readClusterResolver.start();
        return forRegistrationAndDiscovery(writeClusterResolver, readClusterResolver);
    }

    /**
     * TODO: right now this does not work, as internally we try to connect to interest channel as well
     */
    public static <A extends SocketAddress> EurekaClient forRegistration(ServerResolver<A> writeClusterResolver) {
        final BufferedServerResolver<A> bufferedWrite = writeClusterResolver == null ? null : new BufferedServerResolver<>(writeClusterResolver);
        TransportClient writeClient = bufferedWrite == null ? null : TransportClients.newTcpRegistrationClient(inetResolver(bufferedWrite));
        return new EurekaClientImpl(writeClient, null, EurekaClientMetricFactory.clientMetrics());
    }

    /**
     * TODO: right now this does not work, as internally we try to connect to registration channel as well
     */
    public static <A extends SocketAddress> EurekaClient forDiscovery(ServerResolver<A> readClusterResolver, final EurekaClientMetricFactory metricFactory) {
        final BufferedServerResolver<A> bufferedRead = readClusterResolver == null ? null : new BufferedServerResolver<>(readClusterResolver);
        TransportClient readClient = bufferedRead == null ? null : TransportClients.newTcpDiscoveryClient(inetResolver(bufferedRead));
        return new EurekaClientImpl(null, readClient, metricFactory);
    }

    public static <A extends SocketAddress> EurekaClient forDiscovery(ServerResolver<A> readClusterResolver) {
        return forDiscovery(readClusterResolver, EurekaClientMetricFactory.clientMetrics());
    }

    public static EurekaClient forRegistration(String writeClusterDomainName) {
        ServerResolver<InetSocketAddress> writeClusterResolver = ServerResolvers.forDomainName(writeClusterDomainName, ProtocolType.TcpRegistration);
        writeClusterResolver.start();
        return forRegistrationAndDiscovery(writeClusterResolver, (ServerResolver<InetSocketAddress>) null);
    }

    public static EurekaClient forDiscovery(String readClusterDomainName) {
        ServerResolver<InetSocketAddress> readClusterResolver = ServerResolvers.forDomainName(readClusterDomainName, ProtocolType.TcpDiscovery);
        readClusterResolver.start();
        return forRegistrationAndDiscovery(null, readClusterResolver);
    }

    /**
     * TODO: can we demonstrate SocketAddress other than InetSocketAddress?
     */
    @SuppressWarnings("unchecked")
    private static ServerResolver<InetSocketAddress> inetResolver(ServerResolver resolver) {
        return resolver;
    }
}
