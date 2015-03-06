package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;
import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.server.health.KaryonHealthCheckHandler;
import com.netflix.governator.configuration.ArchaiusConfigurationProvider;
import com.netflix.governator.configuration.ArchaiusConfigurationProvider.Builder;
import com.netflix.governator.configuration.ConfigurationOwnershipPolicies;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import io.reactivex.netty.RxNetty;
import netflix.adminresources.AdminResourcesContainer;
import netflix.adminresources.resources.Eureka2InterestClientProvider;
import netflix.adminresources.resources.Eureka2InterestClientProviderImpl;
import netflix.adminresources.resources.StatusRegistry;
import netflix.karyon.archaius.PropertiesLoader;
import netflix.karyon.health.HealthCheckHandler;
import netflix.karyon.health.HealthCheckInvocationStrategy;
import netflix.karyon.health.SyncHealthCheckInvocationStrategy;

/**
 * This class encapsulates Karyon admin UI construction in an embedded environment.
 * In embedded mode services run a different than default or ephemeral ports. If the port is ephemeral
 * its value is established only after server is started. As Karyon admin is driven by Archaius configuration
 * which is singleton in nature running parallel Karyon admin UIs requires extra coordination effort.
 *
 * @author Tomasz Bak
 */
public abstract class EmbeddedKaryonAdminModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AdminResourcesContainer.class).asEagerSingleton();

        bindEureka2RegistryUI();
        bindEureka2StatusUI();

        bind(HealthCheckHandler.class).to(KaryonHealthCheckHandler.class).asEagerSingleton();
        bind(HealthCheckInvocationStrategy.class).to(SyncHealthCheckInvocationStrategy.class).asEagerSingleton();
    }

    public void bindKaryonAdminEnvironment(LifecycleInjectorBuilder bootstrapBinder) {
        bootstrapBinder.withAdditionalBootstrapModules(new BootstrapModule() {
            @Override
            public void configure(BootstrapBinder binder) {
                binder.bind(PropertiesLoader.class).toInstance(new PropertiesLoader() {
                    @Override
                    public void load() {
                        ConfigurationManager.loadProperties(getProperties());
                    }
                });
                binder.bind(PropertiesInitializer.class).asEagerSingleton();

                Builder builder = ArchaiusConfigurationProvider.builder();
                builder.withOwnershipPolicy(ConfigurationOwnershipPolicies.ownsAll());
                binder.bindConfigurationProvider().toInstance(builder.build());
            }
        });
    }

    public void connectToAdminUI() {
        // This is hack to force warming up adminUI singletons, that read Archaius parameters,
        // which itself is singleton, and changes values for each subsequently created new server.
        if (getEurekaWebAdminPort() > 0) {
            RxNetty.createHttpGet("http://localhost:" + getEurekaWebAdminPort() + "/webadmin/eureka2")
                    .materialize().toBlocking().lastOrDefault(null);
        }
    }

    protected abstract Properties getProperties();

    protected abstract int getEurekaWebAdminPort();

    protected abstract int getEurekaHttpServerPort();

    protected abstract ServerResolver getInterestResolver();

    private void bindEureka2RegistryUI() {
        bind(Eureka2InterestClientProvider.class).toInstance(new Eureka2InterestClientProviderImpl() {
            EurekaInterestClient interestClient;

            @Override
            public EurekaInterestClient get() {
                if (interestClient == null) {
                    interestClient = new EurekaInterestClientBuilder()
                            .withServerResolver(getInterestResolver())
                            .build();
                }
                return interestClient;
            }
        });
    }

    private void bindEureka2StatusUI() {
        bind(StatusRegistry.class).toProvider(new Provider<StatusRegistry>() {
            StatusRegistry statusRegistry;

            @Override
            public StatusRegistry get() {
                if (statusRegistry == null) {
                    statusRegistry = new StatusRegistry(getEurekaHttpServerPort());
                    statusRegistry.start();
                }
                return statusRegistry;
            }
        });
    }

    private static class PropertiesInitializer {
        @Inject
        private PropertiesInitializer(PropertiesLoader loader) {
            loader.load();
        }
    }
}
