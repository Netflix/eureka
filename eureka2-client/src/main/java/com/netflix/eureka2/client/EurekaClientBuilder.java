package com.netflix.eureka2.client;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.channel.RegistrationChannelFactory;
import com.netflix.eureka2.client.interest.InterestHandlerImpl;
import com.netflix.eureka2.client.registration.RegistrationHandlerImpl;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registration.RegistrationHandler;
import com.netflix.eureka2.client.interest.InterestHandler;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;

/**
 * @author David Liu
 */
public class EurekaClientBuilder {

    public static final long DEFAULT_RETRY_DELAY_MS = 5000;

    private final boolean doDiscovery;
    private final boolean doRegistration;

    private Long retryDelayMs;
    private ServerResolver readServerResolver;
    private ServerResolver writeServerResolver;

    private EurekaTransportConfig transportConfig;
    private EurekaRegistryConfig registryConfig;

    private EurekaClientMetricFactory metricFactory;
    private EurekaRegistryMetricFactory registryMetricFactory;

    private EurekaClientBuilder(boolean doDiscovery, boolean doRegistration) {
        this.doDiscovery = doDiscovery;
        this.doRegistration = doRegistration;
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

    public EurekaClientBuilder withMetricFactory(EurekaRegistryMetricFactory registryMetricFactory) {
        this.registryMetricFactory = registryMetricFactory;
        return this;
    }

    public EurekaClientBuilder withTransportConfig(EurekaTransportConfig transportConfig) {
        this.transportConfig = transportConfig;
        return this;
    }

    public EurekaClientBuilder withRegistryConfig(EurekaRegistryConfig registryConfig) {
        this.registryConfig = registryConfig;
        return this;
    }

    public EurekaClient build() {
        if (retryDelayMs == null) {
            retryDelayMs = DEFAULT_RETRY_DELAY_MS;
        }

        if (metricFactory == null) {
            metricFactory = EurekaClientMetricFactory.clientMetrics();
        }

        if (registryConfig == null) {
            registryConfig = new BasicEurekaRegistryConfig.Builder().build();
        }

        if (transportConfig == null) {
            transportConfig = new BasicEurekaTransportConfig.Builder().build();
        }

        InterestHandler interestHandler = doDiscovery ? buildInterestHandler() : null;
        RegistrationHandler registrationHandler = doRegistration ? buildRegistrationHandler() : null;

        return new EurekaClientImpl(interestHandler, registrationHandler);
    }

    private InterestHandler buildInterestHandler() {
        if (readServerResolver == null) {
            throw new IllegalArgumentException("Cannot build client for discovery without read server resolver");
        }

        if(registryMetricFactory == null) {
            registryMetricFactory = EurekaRegistryMetricFactory.registryMetrics();
        }

        PreservableEurekaRegistry registry = new PreservableEurekaRegistry(
                new SourcedEurekaRegistryImpl(registryMetricFactory),
                registryConfig,
                registryMetricFactory);

        ClientChannelFactory<InterestChannel> channelFactory
                = new InterestChannelFactory(transportConfig, readServerResolver, registry, metricFactory);

        return new InterestHandlerImpl(registry, channelFactory);
    }

    private RegistrationHandler buildRegistrationHandler() {
        if (writeServerResolver == null) {
            throw new IllegalArgumentException("Cannot build client for registration without write server resolver");
        }

        ClientChannelFactory<RegistrationChannel> channelFactory
                = new RegistrationChannelFactory(transportConfig, writeServerResolver, metricFactory);

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
