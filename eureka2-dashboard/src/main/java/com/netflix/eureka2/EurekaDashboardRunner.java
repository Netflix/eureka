package com.netflix.eureka2;

import java.util.List;

import com.google.inject.Module;
import com.google.inject.Singleton;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.EurekaServerRunner;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;
import com.netflix.karyon.DefaultKaryonConfiguration;
import com.netflix.karyon.Karyon;
import com.netflix.karyon.archaius.ArchaiusKaryonConfiguration;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected List<Module> getModules() {
        Module configModule = (config == null)
                ? EurekaDashboardConfigurationModule.fromArchaius()
                : EurekaDashboardConfigurationModule.fromConfig(config);

        return asList(
                configModule,
                new CommonEurekaServerModule(),
                new EurekaDashboardModule(),
                new KaryonWebAdminModule()
        );
    }

    @Override
    protected LifecycleInjector createInjector() {
        DefaultKaryonConfiguration.Builder configurationBuilder = (name == null)
                ? ArchaiusKaryonConfiguration.builder()
                : ArchaiusKaryonConfiguration.builder().withConfigName(name);

        configurationBuilder
                .addProfile(ServerType.Dashboard.name())
                .addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class));

        return Karyon.createInjector(
                configurationBuilder.build(),
                getModules()
        );
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
