package com.netflix.eureka2;


import java.util.Arrays;

import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.netflix.eureka2.config.DashboardCommandLineParser;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.module.EurekaExtensionModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EurekaDashboardServer extends AbstractEurekaServer<EurekaDashboardConfig> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaDashboardServer.class);

    public EurekaDashboardServer(String name) {
        super(name);
    }

    public EurekaDashboardServer(EurekaDashboardConfig config) {
        super(config);
    }

    @Override
    protected Module getModule() {
        return Modules.combine(Arrays.asList(
                new CommonEurekaServerModule(name),
                new EurekaExtensionModule(ServerType.Dashboard),
                new EurekaDashboardModule(config)
        ));
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Dashboard Server");

        EurekaDashboardConfig config = null;
        if (args.length == 0) {
            logger.info("No command line parameters provided; enabling archaius property loader for server bootstrapping");
        } else {
            DashboardCommandLineParser commandLineParser = new DashboardCommandLineParser(args);
            try {
                config = commandLineParser.process();
            } catch (Exception e) {
                System.err.println("ERROR: invalid configuration parameters; " + e.getMessage());
                System.exit(-1);
            }

            if (commandLineParser.hasHelpOption()) {
                commandLineParser.printHelp();
                System.exit(0);
            }

            logger.info("Server bootstrapping from command line parameters {}", Arrays.toString(args));
        }

        EurekaDashboardServer server = null;
        try {
            server = config != null ? new EurekaDashboardServer(config) : new EurekaDashboardServer("eureka-dashboard-server");
            server.start();
        } catch (Exception e) {
            logger.error("Error while starting Eureka Dashboard server.", e);
            if (server != null) {
                server.shutdown();
            }
            System.exit(-1);
        }
        server.waitTillShutdown();

        // In case we have non-daemon threads running
        System.exit(0);
    }
}
