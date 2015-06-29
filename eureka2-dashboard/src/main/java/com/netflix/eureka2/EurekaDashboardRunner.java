package com.netflix.eureka2;

import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.netflix.eureka2.config.DashboardCommandLineParser;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.EurekaServerRunner;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EurekaDashboardRunner extends EurekaServerRunner<EurekaDashboardConfig, EurekaDashboardServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaDashboardRunner.class);

    public EurekaDashboardRunner(String[] args) {
        super(args, EurekaDashboardServer.class);
    }

    public EurekaDashboardRunner(EurekaDashboardConfig config) {
        super(config, EurekaDashboardServer.class);
    }

    public EurekaDashboardRunner(String name) {
        super(name, EurekaDashboardServer.class);
    }

    @Override
    protected LifecycleInjector createInjector() {
        Module applicationModule = Modules.combine(
                new CommonEurekaServerModule(name),
                new EurekaDashboardModule(config),
                new KaryonWebAdminModule()
        );

        return Governator.createInjector(applicationModule);
    }

    @Override
    protected DashboardCommandLineParser newCommandLineParser(String[] args) {
        return new DashboardCommandLineParser(args);
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Dashboard Server");
        EurekaDashboardRunner runner = args.length == 0 ? new EurekaDashboardRunner("eureka-dashboard-server") : new EurekaDashboardRunner(args);
        if (runner.start()) {
            runner.awaitTermination();
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }
}
