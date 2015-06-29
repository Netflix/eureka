package com.netflix.eureka2.server;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.config.EurekaCommandLineParser;
import com.netflix.eureka2.server.config.WriteCommandLineParser;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.module.EurekaExtensionModule;
import com.netflix.eureka2.server.service.overrides.OverridesModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Bridge (Write) server that captures snapshots of Eureka 1.0 Data and replicates changes of the 1.0 data
 * to other Eureka Write servers.
 *
 * @author David Liu
 */
public class EurekaBridgeServerRunner extends EurekaServerRunner<BridgeServerConfig, EurekaBridgeServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaBridgeServerRunner.class);

    public EurekaBridgeServerRunner(String[] args) {
        super(args, EurekaBridgeServer.class);
    }

    public EurekaBridgeServerRunner(BridgeServerConfig config) {
        super(config, EurekaBridgeServer.class);
    }

    public EurekaBridgeServerRunner(String name) {
        super(name, EurekaBridgeServer.class);
    }

    @Override
    public EurekaBridgeServer getEurekaServer() {
        return injector.getInstance(EurekaBridgeServer.class);
    }

    @Override
    protected LifecycleInjector createInjector() {
        Module configModule = EurekaBridgeServerConfigurationModules.fromArchaius();

        Module applicationModule = Modules.combine(
                configModule,
                new CommonEurekaServerModule(name),
                new OverridesModule(),
                new EurekaExtensionModule(ServerType.Write),
                new EurekaBridgeServerModule(),
                new EurekaModule(),  // eureka 1
                new KaryonWebAdminModule()
        );

        return Governator.createInjector(applicationModule);
    }

    @Override
    protected EurekaCommandLineParser newCommandLineParser(String[] args) {
        return new WriteCommandLineParser(args);
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Dashboard Server");
        EurekaBridgeServerRunner runner = args.length == 0 ? new EurekaBridgeServerRunner("eureka-bridge-server") : new EurekaBridgeServerRunner(args);
        if (runner.start()) {
            runner.awaitTermination();
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }
}
