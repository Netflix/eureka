package com.netflix.eureka2.testkit.embedded.server;

import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.EurekaReadServerConfigurationModule;
import com.netflix.eureka2.server.EurekaReadServerModule;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.interest.FullFetchInterestClient;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.tcp.interest.TcpInterestServer;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.governator.DefaultGovernatorConfiguration;
import com.netflix.governator.DefaultGovernatorConfiguration.Builder;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.auto.ModuleListProviders;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.metric.client.EurekaClientMetricFactory.clientMetrics;
import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServerBuilder extends EmbeddedServerBuilder<EurekaServerConfig, EmbeddedReadServerBuilder> {

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
        ClientChannelFactory<InterestChannel> channelFactory = new InterestChannelFactory(
                serverId,
                transportConfig,
                interestResolver,
                registry,
                clientMetrics()
        );

        EurekaInterestClient interestClient = new FullFetchInterestClient(registry, channelFactory);

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
            bind(TcpInterestServer.class).to(EmbeddedTcpInterestServer.class).in(Scopes.SINGLETON);
        }
    }
}
