package com.netflix.eureka.client.transport;

import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.transport.tcp.TcpDiscoveryClient;
import com.netflix.eureka.client.transport.tcp.TcpRegistrationClient;
import com.netflix.eureka.transport.EurekaTransports;

import java.net.InetSocketAddress;

/**
 * A factory to create {@link TransportClient} instances.
 *
 * @author Nitesh Kant
 */
public final class TransportClients {

    private TransportClients() {
    }

    public static TransportClient newTcpDiscoveryClient(ServerResolver<InetSocketAddress> resolver) {
        return new TcpDiscoveryClient(resolver, EurekaTransports.Codec.Avro);
    }

    public static TransportClient newTcpDiscoveryClient(ServerResolver<InetSocketAddress> resolver,
                                                        EurekaTransports.Codec codec) {
        return new TcpDiscoveryClient(resolver, codec);
    }

    public static TransportClient newTcpRegistrationClient(ServerResolver<InetSocketAddress> resolver) {
        return new TcpRegistrationClient(resolver, EurekaTransports.Codec.Avro);
    }

    public static TransportClient newTcpRegistrationClient(ServerResolver<InetSocketAddress> resolver,
                                                           EurekaTransports.Codec codec) {
        return new TcpRegistrationClient(resolver, codec);
    }
}
