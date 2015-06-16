package com.netflix.discovery;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eventbus.impl.EventBusImpl;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.eventbus.spi.Subscribe;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.LifecycleInjectorMode;
import com.netflix.governator.lifecycle.LifecycleManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class DiscoveryClientEventBusTest extends BaseDiscoveryClientTester {

    private Injector injector;
    private LifecycleManager lifecycleManager;

    @Before
    public void setUp() throws Exception {

        setupProperties();
        // override to make the test faster
        ConfigurationManager.getConfigInstance().setProperty("eureka.client.refresh.interval", 2);

        // disable delta as the mock server does not handle register and subsequent deltas
        ConfigurationManager.getConfigInstance().setProperty("eureka.disableDelta", true);

        // override to test registration path
        ConfigurationManager.getConfigInstance().setProperty("eureka.name", "EurekaTestApp-" + UUID.randomUUID());
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "true");
        ConfigurationManager.getConfigInstance().setProperty("eureka.appinfo.initial.replicate.time", "0");
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                "http://localhost:" + mockLocalEurekaServer.getPort() + MockRemoteEurekaServer.EUREKA_API_BASE_PATH);

        populateLocalRegistryAtStartup();
        populateRemoteRegistryAtStartup();

        // Simulated Child Injectors required for the optional @Injects in DiscoveryClient
        LifecycleInjectorBuilder builder = LifecycleInjector.builder().withMode(LifecycleInjectorMode.SIMULATED_CHILD_INJECTORS);

        Module testModule = Modules.override(new EurekaModule()).with(new AbstractModule() {
            @Override
            protected void configure() {
                // the default impl of EurekaInstanceConfig is CloudInstanceConfig, which we only want in an AWS
                // environment. Here we override that by binding MyDataCenterInstanceConfig to EurekaInstanceConfig.
                bind(EurekaInstanceConfig.class).toProvider(MyDataCenterInstanceConfigProvider.class).in(Scopes.SINGLETON);

                // bind the optional event bus
                bind(EventBus.class).to(EventBusImpl.class).in(Scopes.SINGLETON);
            }
        });

        builder.withModules(testModule);

        injector = builder.build().createInjector();
        lifecycleManager = injector.getInstance(LifecycleManager.class);
        lifecycleManager.start();
    }

    @After
    public void tearDown() {
        if (lifecycleManager != null) {
            lifecycleManager.close();
        }

        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void testStatusChangeEvent() throws Exception {
        final CountDownLatch eventLatch = new CountDownLatch(1);
        final List<StatusChangeEvent> receivedEvents = new ArrayList<StatusChangeEvent>();

        EventBus eventBus = injector.getInstance(EventBus.class);
        eventBus.registerSubscriber(new Object() {
            @Subscribe
            public void consume(StatusChangeEvent event) {
                receivedEvents.add(event);
                eventLatch.countDown();
            }
        });
        // mark status as down. Should see change on next cache refresh
        injector.getInstance(ApplicationInfoManager.class).setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);

        Assert.assertTrue(eventLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, receivedEvents.size());
        Assert.assertNotNull(receivedEvents.get(0));
    }

    @Test
    public void testCacheRefreshEvent() throws Exception {
        final CountDownLatch eventLatch = new CountDownLatch(1);
        final List<CacheRefreshedEvent> receivedEvents = new ArrayList<CacheRefreshedEvent>();

        EventBus eventBus = injector.getInstance(EventBus.class);
        eventBus.registerSubscriber(new Object() {
            @Subscribe
            public void consume(CacheRefreshedEvent event) {
                receivedEvents.add(event);
                eventLatch.countDown();
            }
        });

        Assert.assertTrue(eventLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, receivedEvents.size());
        Assert.assertNotNull(receivedEvents.get(0));
    }
}
