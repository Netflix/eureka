package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.name.Names;
import com.netflix.appinfo.providers.EurekaInstanceConfigFactory;

/**
 * How to use:
 *  - install this module to access all eureka client functionality.
 *
 * Custom config namespace may be registered as follows:
 * <code>
 * InjectorBuilder.fromModules(new EurekaClientModule() {
 *      protected void configureEureka() {
 *          bindEurekaInstanceConfigNamespace().toInstance("namespaceForMyInstanceConfig");
 *          bindEurekaClientConfigNamespace().toInstance("namespaceForMyClientAndTransportConfig");
 *      }
 * }).createInjector()
 * </code>
 *
 * This module support the binding of a custom {@link EurekaInstanceConfigFactory} to supply your own
 * way of providing a config for the creation of an {@link com.netflix.appinfo.InstanceInfo} used for
 * eureka registration.
 *
 * Custom {@link EurekaInstanceConfigFactory} may be registered as follows:
 * <code>
 * InjectorBuilder.fromModules(new EurekaClientModule() {
 *      protected void configureEureka() {
 *          bindEurekaInstanceConfigFactory().to(MyEurekaInstanceConfigFactory.class);
 *      }
 * }).createInjector()
 * </code>
 *
 * Note that this module is NOT compatible with the archaius1 based {@link com.netflix.discovery.guice.EurekaModule}
 *
 * @author David Liu
 */
public class EurekaClientModule extends AbstractModule {

    protected LinkedBindingBuilder<String> bindEurekaInstanceConfigNamespace() {
        return bind(String.class).annotatedWith(Names.named(InternalEurekaClientModule.INSTANCE_CONFIG_NAMESPACE_KEY));
    }

    protected LinkedBindingBuilder<String> bindEurekaClientConfigNamespace() {
        return bind(String.class).annotatedWith(Names.named(InternalEurekaClientModule.CLIENT_CONFIG_NAMESPACE_KEY));
    }

    protected LinkedBindingBuilder<EurekaInstanceConfigFactory> bindEurekaInstanceConfigFactory() {
        return bind(EurekaInstanceConfigFactory.class);
    }

    @Override
    protected void configure() {
        install(new InternalEurekaClientModule());
        configureEureka();
    }

    protected void configureEureka() {

    }
}
