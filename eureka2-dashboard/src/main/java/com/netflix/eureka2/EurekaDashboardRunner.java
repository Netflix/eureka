package com.netflix.eureka2;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.netflix.archaius.inject.ApplicationLayer;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.EurekaServerRunner;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.governator.DefaultGovernatorConfiguration;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

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
    protected List<Module> getModules() {
        Module configModule;

        if (config == null && name == null) {
            configModule = EurekaDashboardConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX);
        } else if (config == null) {  // have name
            configModule = Modules
                    .override(EurekaDashboardConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX))
                    .with(new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(String.class).annotatedWith(ApplicationLayer.class).toInstance(name);
                        }
                    });
        } else {  // have config
            configModule = EurekaDashboardConfigurationModule.fromConfig(config);
        }

        return Arrays.asList(
                configModule,
                new CommonEurekaServerModule(),
                new EurekaDashboardModule(),
                new KaryonWebAdminModule()
        );
    }

    @Override
    protected LifecycleInjector createInjector() {

        return Governator.createInjector(
                DefaultGovernatorConfiguration.builder()
                        .addProfile(ServerType.Dashboard.name())
                        .addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class))
                        .build(),
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
