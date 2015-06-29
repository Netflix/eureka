package com.netflix.eureka2.testkit.embedded.server;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.eureka2.EurekaDashboardModule;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServer.DashboardServerReport;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;

/**
 * @author Tomasz Bak
 */
public class EmbeddedDashboardServerBuilder extends EmbeddedServerBuilder<EurekaDashboardConfig, DashboardServerReport> {

    private EurekaDashboardConfig configuration;
    private ServerResolver registrationResolver;
    private ServerResolver interestResolver;

    public EmbeddedDashboardServerBuilder withConfiguration(EurekaDashboardConfig configuration) {
        this.configuration = configuration;
        return this;
    }

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
                .withTransportConfig(configuration)
                .withRegistryConfig(configuration)
                .withServerResolver(registrationResolver)
                .build();

        final EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(configuration)
                .withRegistryConfig(configuration)
                .withServerResolver(interestResolver)
                .build();

        List<Module> coreModules = new ArrayList<>();

        coreModules.add(new CommonEurekaServerModule());
        coreModules.add(new EurekaDashboardModule(configuration, registrationClient, interestClient));

        List<Module> overrides = new ArrayList<>();
        overrides.add(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        // hack around adminConsole and need for archaius1 bridge
//                        if (config != null) {
//                            ConfigurationManager.getConfigInstance().setProperty(
//                                    "netflix.platform.admin.resources.port", Integer.toString(config.getWebAdminPort()));
//                        }
                    }
                }
        );

        LifecycleInjector injector = Governator.createInjector(Modules.override(Modules.combine(coreModules)).with(overrides));
        return injector.getInstance(EmbeddedDashboardServer.class);
    }
}
