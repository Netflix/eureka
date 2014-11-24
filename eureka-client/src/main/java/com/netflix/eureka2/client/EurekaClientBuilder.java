package com.netflix.eureka2.client;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientChannelFactoryImpl;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registration.RegistrationHandler;
import com.netflix.eureka2.client.registration.RegistrationHandlerImpl;
import com.netflix.eureka2.client.registry.EurekaClientRegistryProxy;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.client.registry.swap.ThresholdStrategy;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.transport.TransportClients;
import com.netflix.eureka2.transport.EurekaTransports;

/**
 * A builder for creating {@link EurekaClient} instances.
 *
 * @author Nitesh Kant
 */
public class EurekaClientBuilder {

    public static final int RECONNECT_RETRY_DELAY = 5000;
    public static final int MIN_REGISTRY_SWAP_PERCENTAGE = 90;
    public static final int RELAX_INTERVAL_MS = 1000;

    private final ServerResolver readServerResolver;
    private final ServerResolver writeServerResolver;

    private EurekaClientMetricFactory metricFactory;
    private EurekaTransports.Codec codec = EurekaTransports.Codec.Avro;
    private long retryDelayMs = RECONNECT_RETRY_DELAY;
    private RegistrySwapStrategyFactory swapStrategyFactory;

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
                retryDelayMs,
                metricFactory
        );
        RegistrationHandler registrationHandler = null;
        if (writeServerResolver != null) {
            registrationHandler = new RegistrationHandlerImpl(channelFactory);
        }
        EurekaClientRegistryProxy registryProxy = null;
        if (readServerResolver != null) {
            if (swapStrategyFactory == null) {
                swapStrategyFactory = ThresholdStrategy.factoryFor(MIN_REGISTRY_SWAP_PERCENTAGE, RELAX_INTERVAL_MS);
            }
            registryProxy = new EurekaClientRegistryProxy(channelFactory, swapStrategyFactory, retryDelayMs, metricFactory);
        }
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

    public EurekaClientBuilder withRetryDelay(long retryDelay, TimeUnit timeUnit) {
        this.retryDelayMs = timeUnit.toMillis(retryDelay);
        return this;
    }

    public EurekaClientBuilder withRegistrySwapStrategyFactory(RegistrySwapStrategyFactory swapStrategyFactory) {
        this.swapStrategyFactory = swapStrategyFactory;
        return this;
    }
}
