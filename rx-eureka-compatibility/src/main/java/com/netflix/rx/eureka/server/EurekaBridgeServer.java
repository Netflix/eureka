package com.netflix.rx.eureka.server;

import com.google.inject.Module;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.LifecycleInjectorBuilderSuite;
import com.netflix.rx.eureka.server.spi.ExtensionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

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
    protected void additionalModules(List<LifecycleInjectorBuilderSuite> suites) {
        suites.add(new LifecycleInjectorBuilderSuite() {
            @Override
            public void configure(LifecycleInjectorBuilder builder) {
                List<Module> all = new ArrayList<>();
                all.add(new EurekaBridgeServerModule(config));

                List<Module> extModules = asList(new ExtensionLoader().asModuleArray());

                all.addAll(extModules);
                builder.withModules(all);
            }
        });
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
