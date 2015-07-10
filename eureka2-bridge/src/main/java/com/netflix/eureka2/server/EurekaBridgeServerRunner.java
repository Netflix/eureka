package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.netflix.archaius.inject.ApplicationLayer;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka2.server.config.BridgeServerConfig;
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
        Module configModule;

        if (config == null && name == null) {
            configModule = EurekaBridgeServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX);
        } else if (config == null) {  // have name
            configModule = Modules
                    .override(EurekaBridgeServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX))
                    .with(new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(String.class).annotatedWith(ApplicationLayer.class).toInstance(name);
                        }
                    });
        } else {  // have config
            configModule = EurekaBridgeServerConfigurationModule.fromConfig(config);
        }

        return Arrays.asList(
                configModule,
                new CommonEurekaServerModule(),
                new EurekaWriteServerModule(),
                new KaryonWebAdminModule(),
                new EurekaModule()  // eureka1
        );
    }

    @Override
    protected LifecycleInjector createInjector() {
        return Governator.createInjector(
                DefaultGovernatorConfiguration.builder()
                        .addProfile(ServerType.Bridge.name())
                        .addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class))
                        .build(),
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
