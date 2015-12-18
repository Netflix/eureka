package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.interest.FullFetchInterestClient2;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.EurekaReadServerConfigurationModule;
import com.netflix.eureka2.server.EurekaReadServerModule;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.EurekaTransportServer;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.governator.DefaultGovernatorConfiguration;
import com.netflix.governator.DefaultGovernatorConfiguration.Builder;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServerBuilder extends EmbeddedServerBuilder<EurekaServerConfig, EmbeddedReadServerBuilder> {

    private static final long RETRY_DELAYS_MS = 5 * 1000;

    private final String serverId;
    private ServerResolver registrationResolver;
    private ServerResolver interestResolver;

    public EmbeddedReadServerBuilder(String serverId) {
        this.serverId = serverId;
    }

    public EmbeddedReadServerBuilder withRegistrationResolver(ServerResolver registrationResolver) {
        this.registrationResolver = registrationResolver;
        return this;
    }

    public EmbeddedReadServerBuilder withInterestResolver(ServerResolver interestResolver) {
        this.interestResolver = interestResolver;
        return this;
    }

    public EmbeddedReadServer build() {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withServerResolver(registrationResolver)
                .build();

        BasicEurekaTransportConfig transportConfig = new BasicEurekaTransportConfig.Builder().build();
        EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(registryMetrics());

        Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.INTERESTED, serverId);
        EurekaClientTransportFactory transportFactory = EurekaClientTransportFactory.getDefaultFactory();
        EurekaInterestClient interestClient = new FullFetchInterestClient2(
                clientSource, registrationResolver, transportFactory, transportConfig, registry, RETRY_DELAYS_MS, Schedulers.computation()
        );

        List<Module> coreModules = new ArrayList<>();

        if (configuration == null) {
            coreModules.add(EurekaReadServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX));
        } else {
            coreModules.add(EurekaReadServerConfigurationModule.fromConfig(configuration));
        }
        coreModules.add(new CommonEurekaServerModule());
        coreModules.add(EurekaReadServerModule.withClients(registrationClient, interestClient));
        if (adminUI) {
            coreModules.add(new EmbeddedKaryonAdminModule(configuration.getEurekaTransport().getWebAdminPort()));
        }

        List<Module> overrides = new ArrayList<>();
        overrides.add(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(AbstractEurekaServer.class).to(EmbeddedReadServer.class);
                    }
                }
        );
        if (networkRouter != null) {
            overrides.add(new NetworkRouterModule(networkRouter));
        }

        Module applicationModules = combineWithExtensionModules(Modules.combine(coreModules));
        applicationModules = combineWithConfigurationOverrides(applicationModules, overrides);

        Builder<?> configurationBuilder = DefaultGovernatorConfiguration.builder().addProfile(ServerType.Read.name());
        if (ext) {
            configurationBuilder.addModuleListProvider(ModuleListProviders.forServiceLoader(ExtAbstractModule.class));
        }
        LifecycleInjector injector = Governator.createInjector(
                configurationBuilder.build(),
                applicationModules
        );

        return injector.getInstance(EmbeddedReadServer.class);
    }

    static class NetworkRouterModule extends AbstractModule {

        private final NetworkRouter networkRouter;

        NetworkRouterModule(NetworkRouter networkRouter) {
            this.networkRouter = networkRouter;
        }

        @Override
        protected void configure() {
            bind(NetworkRouter.class).toInstance(networkRouter);
//            bind(TcpInterestServer.class).to(EmbeddedTcpInterestServer.class).in(Scopes.SINGLETON);
        }

        @Provides
        @Singleton
        public EurekaTransportServer getEurekaTransportServer(EurekaServerTransportFactory transportFactory,
                                                              EurekaServerTransportConfig config,
                                                              EurekaRegistryView registryView,
                                                              EurekaInstanceInfoConfig instanceInfoConfig,
                                                              NetworkRouter networkRouter) {
            EmbeddedEurekaTransportServer server = new EmbeddedEurekaTransportServer(transportFactory, config, null, null, null, registryView, instanceInfoConfig, networkRouter);
            server.start();
            return server;
        }
    }
}
