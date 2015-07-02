package com.netflix.eureka2;

import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.EurekaServerRunner;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

@Singleton
public class EurekaDashboardRunner extends EurekaServerRunner<EurekaDashboardServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaDashboardRunner.class);

    protected final EurekaDashboardConfig config;

    public EurekaDashboardRunner(EurekaDashboardConfig config) {
        super(EurekaDashboardServer.class);
        this.config = config;
    }

    public EurekaDashboardRunner(String name) {
        super(name, EurekaDashboardServer.class);
        this.config = null;
    }

    @Override
    protected LifecycleInjector createInjector() {
        Module configModule = config == null ? EurekaDashboardConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX) :
                EurekaDashboardConfigurationModule.fromConfig(config);

        Module applicationModule = Modules.combine(
                configModule,
                new CommonEurekaServerModule(name),
                new EurekaDashboardModule(),
                new KaryonWebAdminModule()
        );

        return Governator.createInjector(applicationModule);
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Dashboard Server");
        EurekaDashboardRunner runner = new EurekaDashboardRunner("eureka-dashboard-server");
        if (runner.start()) {
            runner.awaitTermination();
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }
}
