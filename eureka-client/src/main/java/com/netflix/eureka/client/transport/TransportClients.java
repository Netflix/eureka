package com.netflix.eureka.client.transport;

import java.net.InetSocketAddress;

import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.transport.tcp.TcpDiscoveryClient;
import com.netflix.eureka.client.transport.tcp.TcpRegistrationClient;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;

/**
 * A factory to create {@link TransportClient} instances.
 *
 * @author Nitesh Kant
 */
public final class TransportClients {
    private static volatile Codec defaultCodec = Codec.Avro;

    private TransportClients() {
    }

    public static TransportClient newTcpDiscoveryClient(ServerResolver<InetSocketAddress> resolver) {
        return new TcpDiscoveryClient(resolver, defaultCodec);
    }

    public static TransportClient newTcpDiscoveryClient(ServerResolver<InetSocketAddress> resolver,
                                                        EurekaTransports.Codec codec) {
        return new TcpDiscoveryClient(resolver, codec);
    }

    public static TransportClient newTcpRegistrationClient(ServerResolver<InetSocketAddress> resolver) {
        return new TcpRegistrationClient(resolver, defaultCodec);
    }

    public static TransportClient newTcpRegistrationClient(ServerResolver<InetSocketAddress> resolver,
                                                           EurekaTransports.Codec codec) {
        return new TcpRegistrationClient(resolver, codec);
    }

    /**
     * It is expected that {@link EurekaClient} will always use single, most optimal codec type.
     * For some situations it may be however desirable to switch it to another type.
     */
    public static void setDefaultCodec(Codec newCodec) {
        defaultCodec = newCodec;
    }
}
