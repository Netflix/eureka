package com.netflix.eureka2.server;

import com.google.inject.Module;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka2.server.config.BridgeServerConfig;
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

import java.util.List;

/**
 * A Bridge (Write) server that captures snapshots of Eureka 1.0 Data and replicates changes of the 1.0 data
 * to other Eureka Write servers.
 *
 * @author David Liu
 */
public class EurekaBridgeServerRunner extends EurekaServerRunner<EurekaBridgeServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaBridgeServerRunner.class);

    protected final BridgeServerConfig config;


    public EurekaBridgeServerRunner(BridgeServerConfig config) {
        super(EurekaBridgeServer.class);
        this.config = config;
    }

    public EurekaBridgeServerRunner(String name) {
        super(name, EurekaBridgeServer.class);
        config = null;
    }

    @Override
    protected List<Module> getModules() {
        Module configModule = (config == null)
                ? EurekaBridgeServerConfigurationModule.fromArchaius()
                : EurekaBridgeServerConfigurationModule.fromConfig(config);

        return asList(
                configModule,
                new CommonEurekaServerModule(),
                new EurekaBridgeServerModule(),
                new KaryonWebAdminModule(),
                new EurekaModule()  // eureka1
        );
    }

    @Override
    protected LifecycleInjector createInjector() {
        DefaultKaryonConfiguration.Builder configurationBuilder = (name == null)
                ? ArchaiusKaryonConfiguration.builder()
                : ArchaiusKaryonConfiguration.builder().withConfigName(name);

        configurationBuilder
                .addProfile(ServerType.Bridge.name())
                .addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class));

        return Karyon.createInjector(
                configurationBuilder.build(),
                getModules()
        );
    }

    public static void main(String[] args) {
        logger.info("Eureka 2.0 Dashboard Server");
        EurekaBridgeServerRunner runner = new EurekaBridgeServerRunner("eureka-bridge-server");
        if (runner.start()) {
            runner.awaitTermination();
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }
}
