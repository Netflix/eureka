package com.netflix.eureka2.client;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.channel.RegistrationChannelFactory;
import com.netflix.eureka2.client.interest.InterestHandlerImpl;
import com.netflix.eureka2.client.registration.RegistrationHandlerImpl;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registration.RegistrationHandler;
import com.netflix.eureka2.client.interest.InterestHandler;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.transport.EurekaTransports;

/**
 * @author David Liu
 */
public class EurekaClientBuilder {

    public static final EurekaTransports.Codec DEFAULT_CODEC = EurekaTransports.Codec.Avro;
    public static final long DEFAULT_RETRY_DELAY_MS = 5000;

    private final boolean doDiscovery;
    private final boolean doRegistration;

    private EurekaTransports.Codec codec;
    private Long retryDelayMs;
    private ServerResolver readServerResolver;
    private ServerResolver writeServerResolver;

    private EurekaClientMetricFactory metricFactory;

    private EurekaClientBuilder(boolean doDiscovery, boolean doRegistration) {
        this.doDiscovery = doDiscovery;
        this.doRegistration = doRegistration;
    }

    public EurekaClientBuilder withCodec(EurekaTransports.Codec codec) {
        this.codec = codec;
        return this;
    }

    public EurekaClientBuilder withRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
        return this;
    }

    public EurekaClientBuilder withReadServerResolver(ServerResolver readServerResolver) {
        this.readServerResolver = readServerResolver;
        return this;
    }

    public EurekaClientBuilder withWriteServerResolver(ServerResolver writeServerResolver) {
        this.writeServerResolver = writeServerResolver;
        return this;
    }

    public EurekaClientBuilder withMetricFactory(EurekaClientMetricFactory clientMetricFactory) {
        this.metricFactory = clientMetricFactory;
        return this;
    }

    public EurekaClient build() {
        if (codec == null) {
            codec = DEFAULT_CODEC;
        }

        if (retryDelayMs == null) {
            retryDelayMs = DEFAULT_RETRY_DELAY_MS;
        }

        if (metricFactory == null) {
            metricFactory = EurekaClientMetricFactory.clientMetrics();
        }

        InterestHandler interestHandler = doDiscovery ? buildInterestHandler() : null;
        RegistrationHandler registrationHandler = doRegistration ? buildRegistrationHandler() : null;

        return new EurekaClientImpl(interestHandler, registrationHandler);
    }

    private InterestHandler buildInterestHandler() {
        if (readServerResolver == null) {
            throw new IllegalArgumentException("Cannot build client for discovery without read server resolver");
        }

        EurekaRegistryMetricFactory registryMetricFactory = EurekaRegistryMetricFactory.registryMetrics();
        PreservableEurekaRegistry registry = new PreservableEurekaRegistry(
                new SourcedEurekaRegistryImpl(registryMetricFactory),
                new BasicEurekaRegistryConfig(),
                registryMetricFactory);

        ClientChannelFactory<InterestChannel> channelFactory
                = new InterestChannelFactory(readServerResolver, codec, registry, metricFactory);

        return new InterestHandlerImpl(registry, channelFactory);
    }

    private RegistrationHandler buildRegistrationHandler() {
        if (writeServerResolver == null) {
            throw new IllegalArgumentException("Cannot build client for registration without write server resolver");
        }

        ClientChannelFactory<RegistrationChannel> channelFactory
                = new RegistrationChannelFactory(writeServerResolver, codec, metricFactory);

        return new RegistrationHandlerImpl(channelFactory);
    }


    public static EurekaClientBuilder newBuilder() {
        return new EurekaClientBuilder(true, true);
    }

    public static EurekaClientBuilder discoveryBuilder() {
        return new EurekaClientBuilder(true, false);
    }

    public static EurekaClientBuilder registrationBuilder() {
        return new EurekaClientBuilder(false, true);
    }

}
