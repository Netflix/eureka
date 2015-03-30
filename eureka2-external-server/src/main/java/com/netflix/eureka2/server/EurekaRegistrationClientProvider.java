package com.netflix.eureka2.server;

import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.server.config.EurekaCommonConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaRegistrationClientProvider implements Provider<EurekaRegistrationClient> {

    private final EurekaCommonConfig config;
    private final EurekaClientMetricFactory clientMetricFactory;
    private final EurekaRegistryMetricFactory registryMetricFactory;

    @Inject
    public EurekaRegistrationClientProvider(EurekaCommonConfig config,
                                            EurekaClientMetricFactory clientMetricFactory,
                                            EurekaRegistryMetricFactory registryMetricFactory) {
        this.config = config;
        this.clientMetricFactory = clientMetricFactory;
        this.registryMetricFactory = registryMetricFactory;
    }

    @Override
    public EurekaRegistrationClient get() {
        return Eureka.newRegistrationClientBuilder()
                .withTransportConfig(config)
                .withRegistryConfig(config)
                .withClientMetricFactory(clientMetricFactory)
                .withRegistryMetricFactory(registryMetricFactory)
                .withServerResolver(WriteClusterResolvers.createRegistrationResolver(config))
                .build();
    }
}
