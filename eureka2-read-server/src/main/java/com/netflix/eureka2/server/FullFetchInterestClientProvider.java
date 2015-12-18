package com.netflix.eureka2.server;


import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.client.interest.FullFetchInterestClient2;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class FullFetchInterestClientProvider implements Provider<FullFetchInterestClient2> {

    private static final String EUREKA_READ_CLIENT_ID = "eurekaReadClient";

    private static final long RETRY_DELAY_MS = 5 * 1000;

    private final EurekaServerConfig config;
    private final EurekaClientTransportFactory transportFactory;
    private final EurekaClientMetricFactory clientMetricFactory;
    private final EurekaRegistryMetricFactory registryMetricFactory;

    private volatile FullFetchInterestClient2 client;

    @Inject
    public FullFetchInterestClientProvider(EurekaServerConfig config,
                                           EurekaClientTransportFactory transportFactory,
                                           EurekaClientMetricFactory clientMetricFactory,
                                           EurekaRegistryMetricFactory registryMetricFactory) {
        this.config = config;
        this.transportFactory = transportFactory;
        this.clientMetricFactory = clientMetricFactory;
        this.registryMetricFactory = registryMetricFactory;
    }

    @Override
    public synchronized FullFetchInterestClient2 get() {
        if (client == null) {
            EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(registryMetricFactory);
            BasicEurekaTransportConfig transportConfig = new BasicEurekaTransportConfig.Builder().build();
            ServerResolver discoveryResolver = WriteClusterResolver.createInterestResolver(config.getEurekaClusterDiscovery());

            // FIXME Use own instance id
            Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.INTERESTED, EUREKA_READ_CLIENT_ID);

            client = new FullFetchInterestClient2(clientSource, discoveryResolver, transportFactory, transportConfig, registry, RETRY_DELAY_MS, Schedulers.computation());
        }

        return client;
    }
}
