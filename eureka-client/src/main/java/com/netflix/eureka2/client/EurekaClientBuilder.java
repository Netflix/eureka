package com.netflix.eureka2.client;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientChannelFactoryImpl;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistryProxy;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.transport.EurekaTransports;

/**
 * A builder for creating {@link EurekaClient} instances.
 *
 * @author Nitesh Kant
 */
public class EurekaClientBuilder {

    private final ServerResolver readServerResolver;
    private final ServerResolver writeServerResolver;
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
        ClientChannelFactory channelFactory = new ClientChannelFactoryImpl(
                writeServerResolver == null ? null : TransportClients.newTcpRegistrationClient(writeServerResolver, codec),
                readServerResolver == null ? null : TransportClients.newTcpDiscoveryClient(readServerResolver, codec),
                metricFactory
        );
        RegistrationHandler registrationHandler = writeServerResolver == null ? null : new RegistrationHandlerImpl(channelFactory);
        EurekaClientRegistryProxy registryProxy = readServerResolver == null ? null : new EurekaClientRegistryProxy(channelFactory, metricFactory);
        return new EurekaClientImpl(registryProxy, registrationHandler);
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
