package com.netflix.eureka2.server;

import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaRegistrationClientProvider implements Provider<EurekaRegistrationClient> {

    private final EurekaServerTransportConfig transportConfig;
    private final EurekaRegistryConfig registryConfig;
    private final EurekaClusterDiscoveryConfig clusterDiscoveryConfig;
    private final EurekaClientMetricFactory clientMetricFactory;
    private final EurekaRegistryMetricFactory registryMetricFactory;

    @Inject
    public EurekaRegistrationClientProvider(EurekaServerTransportConfig transportConfig,
                                            EurekaRegistryConfig registryConfig,
                                            EurekaClusterDiscoveryConfig clusterDiscoveryConfig,
                                            EurekaClientMetricFactory clientMetricFactory,
                                            EurekaRegistryMetricFactory registryMetricFactory) {
        this.transportConfig = transportConfig;
        this.registryConfig = registryConfig;
        this.clusterDiscoveryConfig = clusterDiscoveryConfig;
        this.clientMetricFactory = clientMetricFactory;
        this.registryMetricFactory = registryMetricFactory;
    }

    @Override
    public EurekaRegistrationClient get() {
        return Eurekas.newRegistrationClientBuilder()
                .withTransportConfig(transportConfig)
                .withRegistryConfig(registryConfig)
                .withClientMetricFactory(clientMetricFactory)
                .withRegistryMetricFactory(registryMetricFactory)
                .withServerResolver(WriteClusterResolver.createRegistrationResolver(clusterDiscoveryConfig))
                .build();
    }
}
