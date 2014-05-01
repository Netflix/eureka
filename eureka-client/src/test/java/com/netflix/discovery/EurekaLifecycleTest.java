package com.netflix.discovery;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Test;

import com.google.inject.Injector;
import com.netflix.config.ConfigurationManager;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;

public class EurekaLifecycleTest {
    @After
    public void afterEachTest() {
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void testDefaultInstanceConfig() {
        ConfigurationManager.getConfigInstance().setProperty("eureka.validateInstanceId", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.shouldFetchRegistry", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default", "http://localhost:8080/");

        Injector injector = LifecycleInjector.builder()
                .build()
                .createInjector();

        DiscoveryClient client = injector.getInstance(DiscoveryClient.class);
        Assert.assertEquals(client, DiscoveryManager.getInstance().getDiscoveryClient());
    }

    @Test
    public void testNamespaceOverride() {
        ConfigurationManager.getConfigInstance().setProperty("testnamespace.validateInstanceId", "false");
        ConfigurationManager.getConfigInstance().setProperty("testnamespace.shouldFetchRegistry", "false");
        ConfigurationManager.getConfigInstance().setProperty("testnamespace.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("testnamespace.serviceUrl.default", "http://localhost:8080/");

        Injector injector = LifecycleInjector.builder()
                .withBootstrapModule(new BootstrapModule() {
                    @Override
                    public void configure(BootstrapBinder binder) {
                        binder.bind(String.class).annotatedWith(EurekaNamespace.class).toInstance("testnamespace.");
                    }
                })
                .build()
                .createInjector();

        DiscoveryClient client = injector.getInstance(DiscoveryClient.class);
        Assert.assertEquals(client, DiscoveryManager.getInstance().getDiscoveryClient());
    }
}
