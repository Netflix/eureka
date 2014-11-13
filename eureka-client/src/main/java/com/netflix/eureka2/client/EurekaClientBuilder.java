package com.netflix.eureka2.client;

import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.transport.EurekaTransports;

/**
 * A builder for creating {@link EurekaClient} instances.
 *
 * @author Nitesh Kant
 */
public class EurekaClientBuilder {

    private ServerResolver readServerResolver;
    private ServerResolver writeServerResolver;
    private EurekaClientMetricFactory metricFactory;
    private EurekaTransports.Codec codec = EurekaTransports.Codec.Avro;

    public EurekaClientBuilder(ServerResolver readServerResolver,
                               ServerResolver writeServerResolver) {
        this.readServerResolver = readServerResolver;
        this.writeServerResolver = writeServerResolver;
    }

    public EurekaClient build() {
        if (null == metricFactory) {
            metricFactory = EurekaClientMetricFactory.clientMetrics();
        }
        return new EurekaClientImpl(TransportClients.newTcpRegistrationClient(writeServerResolver, codec),
                                    TransportClients.newTcpDiscoveryClient(readServerResolver, codec), metricFactory);
    }

    public EurekaClientBuilder withMetricFactory(EurekaClientMetricFactory metricFactory) {
        this.metricFactory = metricFactory;
        return this;
    }

    public EurekaClientBuilder withCodec(EurekaTransports.Codec codec) {
        this.codec = codec;
        return this;
    }
}
