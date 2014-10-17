package com.netflix.rx.eureka.client.transport;

import java.net.InetSocketAddress;

import com.netflix.rx.eureka.client.EurekaClient;
import com.netflix.rx.eureka.client.ServerResolver;
import com.netflix.rx.eureka.client.transport.tcp.TcpDiscoveryClient;
import com.netflix.rx.eureka.client.transport.tcp.TcpRegistrationClient;
import com.netflix.rx.eureka.transport.EurekaTransports;
import com.netflix.rx.eureka.transport.EurekaTransports.Codec;

import static com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory.*;

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
        return new TcpDiscoveryClient(resolver, defaultCodec, clientMetrics().getDiscoveryServerConnectionMetrics());
    }

    public static TransportClient newTcpDiscoveryClient(ServerResolver<InetSocketAddress> resolver,
                                                        EurekaTransports.Codec codec) {
        return new TcpDiscoveryClient(resolver, codec, clientMetrics().getDiscoveryServerConnectionMetrics());
    }

    public static TransportClient newTcpRegistrationClient(ServerResolver<InetSocketAddress> resolver) {
        return new TcpRegistrationClient(resolver, defaultCodec, clientMetrics().getRegistrationServerConnectionMetrics());
    }

    public static TransportClient newTcpRegistrationClient(ServerResolver<InetSocketAddress> resolver,
                                                           EurekaTransports.Codec codec) {
        return new TcpRegistrationClient(resolver, codec, clientMetrics().getRegistrationServerConnectionMetrics());
    }

    /**
     * It is expected that {@link EurekaClient} will always use single, most optimal codec type.
     * For some situations it may be however desirable to switch it to another type.
     */
    public static void setDefaultCodec(Codec newCodec) {
        defaultCodec = newCodec;
    }
}
