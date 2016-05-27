package com.netflix.eureka;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.governator.InjectorBuilder;
import com.netflix.governator.LifecycleInjector;

/**
 * Sample Eureka service that registers with Eureka to receive and process requests, using EurekaModule.
 */
public class ExampleEurekaGovernatedService {

    static class ExampleServiceModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(ExampleServiceBase.class).asEagerSingleton();
        }
    }

    private static LifecycleInjector init() throws Exception {
        System.out.println("Creating injector for Example Service");

        LifecycleInjector injector = InjectorBuilder
                .fromModules(new EurekaModule(), new ExampleServiceModule())
                .overrideWith(new AbstractModule() {
                    @Override
                    protected void configure() {
                        DynamicPropertyFactory configInstance = com.netflix.config.DynamicPropertyFactory.getInstance();
                        bind(DynamicPropertyFactory.class).toInstance(configInstance);
                        // the default impl of EurekaInstanceConfig is CloudInstanceConfig, which we only want in an AWS
                        // environment. Here we override that by binding MyDataCenterInstanceConfig to EurekaInstanceConfig.
                        bind(EurekaInstanceConfig.class).to(MyDataCenterInstanceConfig.class);

                        // (DiscoveryClient optional bindings) bind the optional event bus
                        // bind(EventBus.class).to(EventBusImpl.class).in(Scopes.SINGLETON);
                    }
                })
                .createInjector();

        System.out.println("Done creating the injector");
        return injector;
    }

    public static void main(String[] args) throws Exception {
        LifecycleInjector injector = null;
        try {
            injector = init();
            injector.awaitTermination();
        } catch (Exception e) {
            System.out.println("Error starting the sample service: " + e);
            e.printStackTrace();
        } finally {
            if (injector != null) {
                injector.shutdown();
            }
        }
    }

}
