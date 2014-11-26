package com.netflix.eureka2.server;

import java.util.List;

import com.netflix.eureka2.config.BridgeServerConfig;
import com.netflix.eureka2.server.spi.ExtensionLoader;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected void additionalModules(List<BootstrapModule> bootstrapModules) {
        bootstrapModules.add(new BootstrapModule() {
            @Override
            public void configure(BootstrapBinder binder) {
                binder.include(new EurekaBridgeServerModule(config));
            }
        });
        bootstrapModules.add(new ExtensionLoader().asBootstrapModule());
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
