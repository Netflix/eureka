package com.netflix.eureka2.server.module;

import com.google.inject.AbstractModule;
import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import com.netflix.eureka2.server.health.KaryonHealthCheckHandler;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.http.HealthConnectionHandler;
import com.netflix.eureka2.server.service.EurekaShutdownService;
import com.netflix.governator.ProvisionDebugModule;
import netflix.karyon.health.HealthCheckHandler;

/**
 * @author Tomasz Bak
 */
public class CommonEurekaServerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ProvisionDebugModule());

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
    }
}
