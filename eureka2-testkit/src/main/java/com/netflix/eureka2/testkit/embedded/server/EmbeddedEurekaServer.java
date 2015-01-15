package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;
import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.spi.ExtensionLoader;
import com.netflix.governator.configuration.ArchaiusConfigurationProvider;
import com.netflix.governator.configuration.ArchaiusConfigurationProvider.Builder;
import com.netflix.governator.configuration.ConfigurationOwnershipPolicies;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.transformer.OverrideAllDuplicateBindings;
import com.netflix.governator.lifecycle.LifecycleManager;
import io.reactivex.netty.RxNetty;
import netflix.adminresources.AdminResourcesContainer;
import netflix.adminresources.resources.KaryonWebAdminModule;
import netflix.karyon.archaius.PropertiesLoader;
import netflix.karyon.health.AlwaysHealthyHealthCheck;
import netflix.karyon.health.HealthCheckHandler;
import netflix.karyon.health.HealthCheckInvocationStrategy;
import netflix.karyon.health.SyncHealthCheckInvocationStrategy;
import netflix.karyon.servo.KaryonServoModule;

/**
 * @author Tomasz Bak
 */
public abstract class EmbeddedEurekaServer<C extends EurekaCommonConfig, R> {
    private final boolean withExt;
    private final boolean withAdminUI;
    protected final C config;

    protected Injector injector;
    protected LifecycleManager lifecycleManager;

    protected EmbeddedEurekaServer(C config, boolean withExt, boolean withAdminUI) {
        this.config = config;
        this.withExt = withExt;
        this.withAdminUI = withAdminUI;
    }

    public abstract void start();

    public void shutdown() {
        lifecycleManager.close();
    }

    public Injector getInjector() {
        return injector;
    }

    public SourcedEurekaRegistry<InstanceInfo> getEurekaServerRegistry() {
        return injector.getInstance(SourcedEurekaRegistry.class);
    }

    public abstract R serverReport();

    protected void setup(Module[] modules) {
        LifecycleInjectorBuilder builder = LifecycleInjector.builder()
                .withModuleTransformer(new OverrideAllDuplicateBindings());
        builder.withAdditionalModules(modules);

        // Extensions
        builder.withAdditionalModules(new ExtensionLoader(!withExt).asModuleArray());

        // Admin console
        if (withAdminUI) {
            bindConfigurationProvider(builder);
            bindDashboard(builder);
        }

        injector = builder.build().createInjector();

        lifecycleManager = injector.getInstance(LifecycleManager.class);
        try {
            lifecycleManager.start();

            // This is hack to force warming up adminUI singletons, that read Archaius parameters,
            // which itself is singleton, and changes values for each subsequently created new server.
            if (withAdminUI) {
                RxNetty.createHttpGet("http://localhost:" + config.getWebAdminPort() + "/webadmin/eureka2")
                        .materialize().toBlocking().lastOrDefault(null);
            }
        } catch (Exception e) {
            throw new RuntimeException("Container setup failure", e);
        }
    }

    protected String formatAdminURI() {
        return "http://localhost:" + config.getWebAdminPort() + "/admin";
    }

    private void bindDashboard(LifecycleInjectorBuilder builder) {
        builder.withAdditionalModuleClasses(KaryonWebAdminModule.class);
        builder.withAdditionalModuleClasses(KaryonServoModule.class);
        builder.withAdditionalModules(new AbstractModule() {
            @Override
            protected void configure() {
                bind(HealthCheckHandler.class).to(AlwaysHealthyHealthCheck.class).asEagerSingleton();
                bind(HealthCheckInvocationStrategy.class).to(SyncHealthCheckInvocationStrategy.class).asEagerSingleton();
            }
        });
    }

    protected void bindConfigurationProvider(LifecycleInjectorBuilder bootstrapBinder) {
        final Properties props = new Properties();
        loadInstanceProperties(props);

        bootstrapBinder.withAdditionalBootstrapModules(new BootstrapModule() {
            @Override
            public void configure(BootstrapBinder binder) {
                binder.bind(PropertiesLoader.class).toInstance(new PropertiesLoader() {
                    @Override
                    public void load() {
                        ConfigurationManager.loadProperties(props);
                    }
                });
                binder.bind(PropertiesInitializer.class).asEagerSingleton();

                Builder builder = ArchaiusConfigurationProvider.builder();
                builder.withOwnershipPolicy(ConfigurationOwnershipPolicies.ownsAll());
                binder.bindConfigurationProvider().toInstance(builder.build());
            }
        });
    }

    protected void loadInstanceProperties(Properties props) {
        props.setProperty(AdminResourcesContainer.CONTAINER_LISTEN_PORT, Integer.toString(config.getWebAdminPort()));
        props.setProperty("netflix.platform.admin.pages.packages", "netflix");
    }

    private static class PropertiesInitializer {
        @Inject
        private PropertiesInitializer(PropertiesLoader loader) {
            loader.load();
        }
    }
}
