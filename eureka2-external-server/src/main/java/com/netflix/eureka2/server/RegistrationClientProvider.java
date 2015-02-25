package com.netflix.eureka2.server;

import javax.inject.Provider;

import com.google.inject.Inject;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.client.registration.EurekaRegistrationClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.server.config.EurekaCommonConfig;

/**
 * @author Tomasz Bak
 */
public class RegistrationClientProvider implements Provider<EurekaRegistrationClient> {

    private final EurekaCommonConfig config;
    private final EurekaClientMetricFactory clientMetricFactory;
    private final EurekaRegistryMetricFactory registryMetricFactory;

    @Inject
    public RegistrationClientProvider(EurekaCommonConfig config,
                                      EurekaClientMetricFactory clientMetricFactory,
                                      EurekaRegistryMetricFactory registryMetricFactory) {
        this.config = config;
        this.clientMetricFactory = clientMetricFactory;
        this.registryMetricFactory = registryMetricFactory;
    }

    @Override
    public EurekaRegistrationClient get() {
        ServerResolver registrationResolver = WriteClusterResolvers.createRegistrationResolver(config);
        return EurekaClientBuilder.registrationBuilder()
                .withClientMetricFactory(clientMetricFactory)
                .withWriteServerResolver(registrationResolver)
                .withRegistryMetricFactory(registryMetricFactory)
                .build();
    }
}
