package com.netflix.eureka2.client.transport;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.tcp.TcpDiscoveryClient;
import com.netflix.eureka2.client.transport.tcp.TcpRegistrationClient;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.transport.TransportClient;

import static com.netflix.eureka2.metric.client.EurekaClientMetricFactory.clientMetrics;

/**
 * A factory to create {@link com.netflix.eureka2.transport.TransportClient} instances.
 *
 * TODO Do not use default metrics factory. Pass it directly as method argument.
 *
 * @author Nitesh Kant
 */
public final class TransportClients {

    private TransportClients() {
    }

    public static TransportClient newTcpDiscoveryClient(EurekaTransportConfig config, ServerResolver resolver) {
        return new TcpDiscoveryClient(config, resolver, clientMetrics().getDiscoveryServerConnectionMetrics());
    }

    public static TransportClient newTcpRegistrationClient(EurekaTransportConfig config, ServerResolver resolver) {
        return new TcpRegistrationClient(config, resolver, clientMetrics().getRegistrationServerConnectionMetrics());
    }
}
