package com.netflix.eureka2.testkit.embedded.server;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.eureka2.EurekaDashboardConfigurationModule;
import com.netflix.eureka2.EurekaDashboardModule;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;

import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
public class EmbeddedDashboardServerBuilder extends EmbeddedServerBuilder<EurekaDashboardConfig, EmbeddedDashboardServerBuilder> {

    private ServerResolver registrationResolver;
    private ServerResolver interestResolver;

    public EmbeddedDashboardServerBuilder withRegistrationResolver(ServerResolver registrationResolver) {
        this.registrationResolver = registrationResolver;
        return this;
    }

    public EmbeddedDashboardServerBuilder withInterestResolver(ServerResolver interestResolver) {
        this.interestResolver = interestResolver;
        return this;
    }

    public EmbeddedDashboardServer build() {
        final EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withTransportConfig(configuration.getEurekaTransport())
                .withRegistryConfig(configuration.getEurekaRegistry())
                .withServerResolver(registrationResolver)
                .build();

        final EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(configuration.getEurekaTransport())
                .withRegistryConfig(configuration.getEurekaRegistry())
                .withServerResolver(interestResolver)
                .build();

        List<Module> coreModules = new ArrayList<>();

        if (configuration == null) {
            coreModules.add(EurekaDashboardConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX));
        } else {
            coreModules.add(EurekaDashboardConfigurationModule.fromConfig(configuration));
        }

        coreModules.add(new CommonEurekaServerModule());
        coreModules.add(EurekaDashboardModule.withClients(registrationClient, interestClient));

        LifecycleInjector injector = Governator.createInjector(Modules.combine(coreModules));
        return injector.getInstance(EmbeddedDashboardServer.class);
    }
}
