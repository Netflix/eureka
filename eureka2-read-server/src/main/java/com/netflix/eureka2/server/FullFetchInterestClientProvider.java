package com.netflix.eureka2.server;


import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.BatchAwareIndexRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.interest.FullFetchBatchingRegistry;
import com.netflix.eureka2.server.interest.FullFetchInterestClient;

/**
 * @author Tomasz Bak
 */
public class FullFetchInterestClientProvider implements Provider<FullFetchInterestClient> {

    private static final String EUREKA_READ_CLIENT_ID = "eurekaReadClient";

    private final EurekaServerConfig config;
    private final EurekaClientMetricFactory clientMetricFactory;
    private final EurekaRegistryMetricFactory registryMetricFactory;

    @Inject
    public FullFetchInterestClientProvider(EurekaServerConfig config,
                                           EurekaClientMetricFactory clientMetricFactory,
                                           EurekaRegistryMetricFactory registryMetricFactory) {
        this.config = config;
        this.clientMetricFactory = clientMetricFactory;
        this.registryMetricFactory = registryMetricFactory;
    }

    @Override
    public FullFetchInterestClient get() {
        BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new FullFetchBatchingRegistry<>();
        BatchAwareIndexRegistry<InstanceInfo> indexRegistry = new BatchAwareIndexRegistry<>(
                new IndexRegistryImpl<InstanceInfo>(), remoteBatchingRegistry);

        BasicEurekaTransportConfig transportConfig = new BasicEurekaTransportConfig.Builder().build();

        SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(indexRegistry, registryMetricFactory);
        ServerResolver discoveryResolver = WriteClusterResolver.createInterestResolver(config.getEurekaClusterDiscovery());

        ClientChannelFactory<InterestChannel> channelFactory = new InterestChannelFactory(
                EUREKA_READ_CLIENT_ID,
                transportConfig,
                discoveryResolver,
                registry,
                remoteBatchingRegistry,
                clientMetricFactory
        );

        return new FullFetchInterestClient(registry, channelFactory);
    }
}
