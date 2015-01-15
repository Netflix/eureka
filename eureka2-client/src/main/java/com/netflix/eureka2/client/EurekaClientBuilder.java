package com.netflix.eureka2.client;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientChannelFactoryImpl;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registration.RegistrationHandler;
import com.netflix.eureka2.client.registration.RegistrationHandlerImpl;
import com.netflix.eureka2.client.interest.InterestHandler;
import com.netflix.eureka2.client.interest.InterestHandlerImpl;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.transport.EurekaTransports;

/**
 * A builder for creating {@link EurekaClient} instances.
 *
 * @author Nitesh Kant
 */
public class EurekaClientBuilder {

    public static final int RECONNECT_RETRY_DELAY = 5000;

    private final ServerResolver readServerResolver;
    private final ServerResolver writeServerResolver;

    private EurekaClientMetricFactory clientMetricFactory;
    private EurekaRegistryMetricFactory registryMetricFactory;
    private EurekaTransports.Codec codec = EurekaTransports.Codec.Avro;
    private long retryDelayMs = RECONNECT_RETRY_DELAY;

    public EurekaClientBuilder(ServerResolver readServerResolver,
                               ServerResolver writeServerResolver) {
        this.readServerResolver = readServerResolver;
        this.writeServerResolver = writeServerResolver;
    }

    public EurekaClient build() {
        if (null == clientMetricFactory) {
            clientMetricFactory = EurekaClientMetricFactory.clientMetrics();
        }

        if (null == registryMetricFactory) {
            registryMetricFactory = EurekaRegistryMetricFactory.registryMetrics();
        }

        PreservableEurekaRegistry registry = null;
        if (readServerResolver != null) {
            registry = new PreservableEurekaRegistry(
                    new SourcedEurekaRegistryImpl(registryMetricFactory),
                    new BasicEurekaRegistryConfig(),
                    registryMetricFactory);
        }

        ClientChannelFactory channelFactory = new ClientChannelFactoryImpl(
                writeServerResolver == null ? null : TransportClients.newTcpRegistrationClient(writeServerResolver, codec),
                readServerResolver == null ? null : TransportClients.newTcpDiscoveryClient(readServerResolver, codec),
                registry,
                retryDelayMs,
                clientMetricFactory
        );

        RegistrationHandler registrationHandler = null;
        if (writeServerResolver != null) {
            registrationHandler = new RegistrationHandlerImpl(channelFactory);
        }
        InterestHandler interestHandler = null;
        if (readServerResolver != null) {
            interestHandler = new InterestHandlerImpl(registry, channelFactory);
        }
        return new EurekaClientImpl(interestHandler, registrationHandler);
    }

    public EurekaClientBuilder withMetricFactory(EurekaClientMetricFactory metricFactory) {
        this.clientMetricFactory = metricFactory;
        return this;
    }

    public EurekaClientBuilder withCodec(EurekaTransports.Codec codec) {
        this.codec = codec;
        return this;
    }
}
