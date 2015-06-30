package com.netflix.eureka2.server.module;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.archaius.bridge.StaticArchaiusBridgeModule;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.archaius.inject.ApplicationLayer;
import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import com.netflix.eureka2.server.health.KaryonHealthCheckHandler;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.http.HealthConnectionHandler;
import com.netflix.eureka2.server.service.EurekaShutdownService;
import com.netflix.governator.ConfigurationModule;
import com.netflix.governator.configuration.ConfigurationProvider;
import netflix.karyon.health.HealthCheckHandler;

/**
 * @author Tomasz Bak
 */
public class CommonEurekaServerModule extends AbstractModule {

    private final String name;

    public CommonEurekaServerModule() {
        this(null);
    }

    public CommonEurekaServerModule(String name) {
        this.name = name;
    }

    @Override
    protected void configure() {
        // configurations
        Module configurations;
        if (name != null) {
            configurations = Modules.override(new ArchaiusModule()).with(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(String.class).annotatedWith(ApplicationLayer.class).toInstance(name);
                }
            });
        } else {
            configurations = new ArchaiusModule();
        }

        bind(ConfigurationProvider.class).to(Archaius2ConfigurationProvider.class);
        install(configurations);
        install(new ConfigurationModule());

        // metrics
        install(new SpectatorDefaultMetricsModule());
//        install(new SpectatorCodahaleMetricsModule()); // Loaded only if codehala metrics are available on classpath

        // common eureka server functions
        bind(EurekaShutdownService.class).asEagerSingleton();
        bind(HealthCheckHandler.class).to(KaryonHealthCheckHandler.class).asEagerSingleton();
        bind(EurekaHttpServer.class).asEagerSingleton();

        // health
        bind(HealthConnectionHandler.class).asEagerSingleton();
        bind(EurekaHealthStatusAggregator.class).to(EurekaHealthStatusAggregatorImpl.class).asEagerSingleton();
        install(new EurekaHealthStatusModule());

        // web admin
        install(new StaticArchaiusBridgeModule());  // required to bridge archaius1 that is still used by adminModule
    }
}
