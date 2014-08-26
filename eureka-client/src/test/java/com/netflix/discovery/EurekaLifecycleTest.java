package com.netflix.discovery;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Test;

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
    public void testBackupRegistryInjection() {
        ConfigurationManager.getConfigInstance().setProperty("eureka.validateInstanceId", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.shouldFetchRegistry", "true");
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default", "http://localhost:8080/");

        Injector injector = LifecycleInjector.builder()
                                             .withAdditionalModules(new AbstractModule() {
                                                 @Override
                                                 protected void configure() {
                                                     bind(BackupRegistry.class).to(MockBackupRegistry.class);
                                                 }
                                             }).build()
                                             .createInjector();

        MockBackupRegistry backupRegistry = (MockBackupRegistry) injector.getInstance(BackupRegistry.class);
        Applications apps = new Applications();
        String dummyappName = "dummyapp";
        Application dummyapp = new Application(dummyappName);
        apps.addApplication(dummyapp);
        dummyapp.addInstance(InstanceInfo.Builder.newBuilder().setHostName("host").setAppName(dummyappName).build());
        backupRegistry.setLocalRegionApps(apps);

        DiscoveryClient client = injector.getInstance(DiscoveryClient.class);

        Assert.assertEquals(client, DiscoveryManager.getInstance().getDiscoveryClient());
        Assert.assertNotNull("Application not returned from the backup.", client.getApplication(dummyappName));
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
