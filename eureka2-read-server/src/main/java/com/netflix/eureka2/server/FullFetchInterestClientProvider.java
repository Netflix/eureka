package com.netflix.eureka2.server;


import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.interest.FullFetchInterestClient;

/**
 * @author Tomasz Bak
 */
public class FullFetchInterestClientProvider implements Provider<FullFetchInterestClient> {

    private static final String EUREKA_READ_CLIENT_ID = "eurekaReadClient";

    private final EurekaServerConfig config;
    private final EurekaClientMetricFactory clientMetricFactory;
    private final EurekaRegistryMetricFactory registryMetricFactory;

    private volatile FullFetchInterestClient client;

    @Inject
    public FullFetchInterestClientProvider(EurekaServerConfig config,
                                           EurekaClientMetricFactory clientMetricFactory,
                                           EurekaRegistryMetricFactory registryMetricFactory) {
        this.config = config;
        this.clientMetricFactory = clientMetricFactory;
        this.registryMetricFactory = registryMetricFactory;
    }

    @Override
    public synchronized FullFetchInterestClient get() {
        if (client == null) {
            EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(registryMetricFactory);
            BasicEurekaTransportConfig transportConfig = new BasicEurekaTransportConfig.Builder().build();
            ServerResolver discoveryResolver = WriteClusterResolver.createInterestResolver(config.getEurekaClusterDiscovery());

            ClientChannelFactory<InterestChannel> channelFactory = new InterestChannelFactory(
                    EUREKA_READ_CLIENT_ID,
                    transportConfig,
                    discoveryResolver,
                    registry,
                    clientMetricFactory
            );

            client = new FullFetchInterestClient(registry, channelFactory);
        }

        return client;
    }
}
