package com.netflix.eureka2.server;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.module.EurekaExtensionModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * A Bridge (Write) server that captures snapshots of Eureka 1.0 Data and replicates changes of the 1.0 data
 * to other Eureka Write servers.
 *
 * @author David Liu
 */
public class EurekaBridgeServer extends AbstractEurekaServer<BridgeServerConfig> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaBridgeServer.class);

    public EurekaBridgeServer(String name) {
        super(name);
    }

    @Override
    protected Module getModule() {
        return Modules.combine(Arrays.asList(
                new CommonEurekaServerModule(name),
                new EurekaExtensionModule(ServerType.Write),
                new EurekaBridgeServerModule(config),
                new EurekaModule()  // eureka 1
        ));
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Bridge Server");

        EurekaBridgeServer server = null;
        try {
            server = new EurekaBridgeServer("eureka-bridge-server");
            server.start();
        } catch (Exception e) {
            logger.error("Error while starting Eureka Bridge server.", e);
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
