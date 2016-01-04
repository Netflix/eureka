package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.util.Modules;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.EurekaWriteServerConfigurationModule;
import com.netflix.eureka2.server.EurekaWriteServerModule;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.EurekaTransportServer;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.governator.DefaultGovernatorConfiguration;
import com.netflix.governator.DefaultGovernatorConfiguration.Builder;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import rx.Observable;

import static com.netflix.eureka2.Names.EUREKA_SERVICE;
import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteServerBuilder extends EmbeddedServerBuilder<WriteServerConfig, EmbeddedWriteServerBuilder> {

    private Observable<ChangeNotification<Server>> replicationPeers;

    public EmbeddedWriteServerBuilder withReplicationPeers(Observable<ChangeNotification<Server>> replicationPeers) {
        this.replicationPeers = replicationPeers;
        return this;
    }

    public EmbeddedWriteServer build() {
        List<Module> coreModules = new ArrayList<>();

        if (configuration == null) {
            coreModules.add(EurekaWriteServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX));
        } else {
            coreModules.add(EurekaWriteServerConfigurationModule.fromConfig(configuration));
        }
        coreModules.add(new CommonEurekaServerModule());
        coreModules.add(new EmbeddedEurekaWriteServerModule(networkRouter));

        if (adminUI) {
            coreModules.add(new EmbeddedKaryonAdminModule(configuration.getEurekaTransport().getWebAdminPort()));
        }

        List<Module> overrides = new ArrayList<>();
        overrides.add(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                        bind(AbstractEurekaServer.class).to(EmbeddedWriteServer.class);
                    }
                }
        );

        Module applicationModules = combineWithExtensionModules(Modules.combine(coreModules));
        applicationModules = combineWithConfigurationOverrides(applicationModules, overrides);

        Builder<?> configurationBuilder = DefaultGovernatorConfiguration.builder().addProfile(ServerType.Write.name());
        if (ext) {
            configurationBuilder.addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class));
        }
        LifecycleInjector injector = Governator.createInjector(
                configurationBuilder.build(),
                applicationModules
        );
        return injector.getInstance(EmbeddedWriteServer.class);
    }

    static class EmbeddedEurekaWriteServerModule extends EurekaWriteServerModule {

        private final NetworkRouter networkRouter;

        EmbeddedEurekaWriteServerModule(NetworkRouter networkRouter) {
            this.networkRouter = networkRouter;
        }

        @Override
        protected void configure() {
            super.configure();
            if (networkRouter != null) {
                bind(NetworkRouter.class).toInstance(networkRouter);
            }
        }

        @Override
        protected void bindEurekaTransportServer() {
            if (networkRouter == null) {
                super.bindEurekaTransportServer();
            } else {
                bind(EurekaTransportServer.class).toProvider(EmbeddedWriteServerTransportProvider.class);
            }
        }

        @Singleton
        static class EmbeddedWriteServerTransportProvider implements Provider<EurekaTransportServer> {

            private final EmbeddedEurekaTransportServer transportServer;

            @Inject
            EmbeddedWriteServerTransportProvider(EurekaServerTransportFactory transportFactory,
                                                 EurekaServerTransportConfig config,
                                                 @Named(EUREKA_SERVICE) Provider<EurekaRegistrationProcessor> registrationProcessor,
                                                 @Named(EUREKA_SERVICE) MetricEventsListenerFactory servoEventsListenerFactory,
                                                 EurekaRegistry registry,
                                                 EurekaRegistryView registryView,
                                                 EurekaInstanceInfoConfig instanceInfoConfig,
                                                 NetworkRouter networkRouter) {
                this.transportServer = new EmbeddedEurekaTransportServer(transportFactory, config, registrationProcessor, servoEventsListenerFactory, registry, registryView, instanceInfoConfig, networkRouter);
                this.transportServer.start();
            }

            @Override
            public EurekaTransportServer get() {
                return transportServer;
            }
        }
    }
}
