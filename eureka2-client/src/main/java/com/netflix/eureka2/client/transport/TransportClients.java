package com.netflix.eureka2.client.transport;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.tcp.TcpInterestClient;
import com.netflix.eureka2.client.transport.tcp.TcpRegistrationClient;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.transport.TransportClient;

/**
 * A factory to create {@link com.netflix.eureka2.transport.TransportClient} instances.
 *
 * @author Nitesh Kant
 */
public final class TransportClients {

    private TransportClients() {
    }

    public static TransportClient newTcpInterestClient(String clientId,
                                                       EurekaTransportConfig config,
                                                       ServerResolver resolver,
                                                       EurekaClientMetricFactory metricFactory) {
        return new TcpInterestClient(clientId, config, resolver, metricFactory.getDiscoveryServerConnectionMetrics());
    }

    public static TransportClient newTcpRegistrationClient(String clientId,
                                                           EurekaTransportConfig config,
                                                           ServerResolver resolver,
                                                           EurekaClientMetricFactory metricFactory) {
        return new TcpRegistrationClient(clientId, config, resolver, metricFactory.getRegistrationServerConnectionMetrics());
    }
}
