package com.netflix.discovery.lifecycle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Supplier;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryClientProxy;
import com.netflix.discovery.MockRemoteEurekaServer;
import com.netflix.discovery.shared.Application;
import com.netflix.eventbus.impl.EventBusImpl;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.governator.annotations.binding.DownStatus;
import com.netflix.governator.annotations.binding.UpStatus;
import com.netflix.governator.configuration.PropertiesConfigurationProvider;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;

public class DiscoveryStatusCheckerTest {
    
    public static final String ALL_REGIONS_VIP_ADDR = "myvip";
    public static final String REMOTE_REGION_INSTANCE_1_HOSTNAME = "blah";
    public static final String REMOTE_REGION_INSTANCE_2_HOSTNAME = "blah2";

    public static final String LOCAL_REGION_APP_NAME = "MYAPP_LOC";
    public static final String LOCAL_REGION_INSTANCE_1_HOSTNAME = "blahloc";
    public static final String LOCAL_REGION_INSTANCE_2_HOSTNAME = "blahloc2";

    public static final String REMOTE_REGION_APP_NAME = "MYAPP";
    public static final String REMOTE_REGION = "myregion";
    public static final String REMOTE_ZONE = "myzone";
    public static final int CLIENT_REFRESH_RATE = 6;
    public static final long STATUS_REFRESH_RATE = 3L;

    private MockRemoteEurekaServer mockLocalEurekaServer;
    private final Map<String, Application> localRegionApps = new HashMap<String, Application>();
    private final Map<String, Application> localRegionAppsDelta = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionApps = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionAppsDelta = new HashMap<String, Application>();
    
    private DiscoveryClientProxy client;
    private InstanceInfo instanceInfo;
    private final int localRandomEurekaPort = 7799;
    private static EventBus eventBus = new EventBusImpl();
    
    @Before
    public void setUp() throws Exception {
        final int eurekaPort = localRandomEurekaPort + (int)(Math.random() * 10);
        ConfigurationManager.getConfigInstance().setProperty("eureka.client.refresh.interval", CLIENT_REFRESH_RATE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.fetchRemoteRegionsRegistry", REMOTE_REGION);
        ConfigurationManager.getConfigInstance().setProperty("eureka.myregion.availabilityZones", REMOTE_ZONE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                                                             "http://localhost:" + eurekaPort +
                                                             MockRemoteEurekaServer.EUREKA_API_BASE_PATH);
        populateLocalRegistryAtStartup();
        populateRemoteRegistryAtStartup();

        mockLocalEurekaServer = new MockRemoteEurekaServer(eurekaPort, localRegionApps, localRegionAppsDelta,
                                                           remoteRegionApps, remoteRegionAppsDelta);
        mockLocalEurekaServer.start();

        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
        builder.setIPAddr("10.10.101.00");
        builder.setHostName(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        builder.setAppName(LOCAL_REGION_APP_NAME);
        builder.setStatus(InstanceStatus.OUT_OF_SERVICE);
        builder.setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });
        
        instanceInfo = builder.build();
        client = new DiscoveryClientProxy(instanceInfo, new DefaultEurekaClientConfig(), eventBus);
    }
    
    
    public static class Service {
        final Supplier<Boolean> upStatusSupplier;
        final Supplier<Boolean> dnStatusSupplier;
        final DiscoveryClient client;
        
        @Inject
        public Service(
                DiscoveryClient client,
                @UpStatus   Supplier<Boolean> upStatusSupplier,
                @DownStatus Supplier<Boolean> dnStatusSupplier
                ) {
            this.client = client;
            this.upStatusSupplier = upStatusSupplier;
            this.dnStatusSupplier = dnStatusSupplier;
        }
        
        public void assertState(boolean state) {
            Assert.assertEquals(state, (boolean)upStatusSupplier.get());
            Assert.assertEquals(state, !dnStatusSupplier.get());
        }
    }
    
    @Test
    public void testStatus() throws Exception {
        Injector injector = LifecycleInjector.builder()
                .withBootstrapModule(new BootstrapModule() {
                    @Override
                    public void configure(BootstrapBinder binder) {
                        Properties props = new Properties();
                        props.put("eureka.status.interval", STATUS_REFRESH_RATE);

                        binder.bindConfigurationProvider().toInstance(new PropertiesConfigurationProvider(props));
                    }
                })
                .withRootModule(InternalEurekaStatusModule.class)
                .withModules(
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(EventBus.class).toInstance(eventBus);
                            bind(Service.class);
                            bind(DiscoveryClient.class).toInstance(client.getClient());
                            bind(InstanceInfo.class).toInstance(instanceInfo);
                        }
                    })
                .build()
                .createInjector();
        
        Service service = injector.getInstance(Service.class);
        waitForDeltaToBeRetrieved();
        service.assertState(true);
        
        client.unregister();
        waitForDeltaToBeRetrieved();
        TimeUnit.SECONDS.sleep(CLIENT_REFRESH_RATE * 2);
//        service.assertState(false);
        
        // TODO: More work needs to be done on the MockEurekaServer to properly support this
        //       This is disabled for now until MockEurekaServer can be updated
//        System.out.println("****** Re-register");
//        client.register();
//        waitForDeltaToBeRetrieved();
//        TimeUnit.SECONDS.sleep(CLIENT_REFRESH_RATE * 2);
//        service.assertState(true);
    }
    
    private void populateLocalRegistryAtStartup() {
        Application myapp = createLocalApps();
        Application myappDelta = createLocalAppsDelta();
        localRegionApps.put(LOCAL_REGION_APP_NAME, myapp);
        localRegionAppsDelta.put(LOCAL_REGION_APP_NAME, myappDelta);
    }

    private Application createLocalApps() {
        Application myapp = new Application(LOCAL_REGION_APP_NAME);
        InstanceInfo instanceInfo = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private Application createLocalAppsDelta() {
        Application myapp = new Application(LOCAL_REGION_APP_NAME);
        InstanceInfo instanceInfo = createLocalInstance(LOCAL_REGION_INSTANCE_2_HOSTNAME);
        instanceInfo.setActionType(InstanceInfo.ActionType.ADDED);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private InstanceInfo createLocalInstance(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(LOCAL_REGION_APP_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP_ADDR);
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        AmazonInfo amazonInfo = getAmazonInfo(null, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        return instanceBuilder.build();
    }

    private AmazonInfo getAmazonInfo(@Nullable String availabilityZone, String instanceHostName) {
        AmazonInfo.Builder azBuilder = AmazonInfo.Builder.newBuilder();
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.availabilityZone, (null == availabilityZone) ? "us-east-1a" : availabilityZone);
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.instanceId, instanceHostName);
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.amiId, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.instanceType, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.localIpv4, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.publicIpv4, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.publicHostname, instanceHostName);
        return azBuilder.build();
    }
    
    private void checkInstancesFromARegion(String region, String instance1Hostname, String instance2Hostname) {
        List<InstanceInfo> instancesByVipAddress;
        if (region.equals("local")) {
            instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false);
        } else {
            instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false, region);
        }
        Assert.assertEquals("Unexpected number of instances found for " + region + " region.", 2,
                            instancesByVipAddress.size());
        InstanceInfo localInstance1 = null;
        InstanceInfo localInstance2 = null;
        for (InstanceInfo instance : instancesByVipAddress) {
            if (instance.getHostName().equals(instance1Hostname)) {
                localInstance1 = instance;
            } else if (instance.getHostName().equals(instance2Hostname)) {
                localInstance2 = instance;
            }
        }

        Assert.assertNotNull("Expected instance not returned for " + region + " region vip address", localInstance1);
        Assert.assertNotNull("Instance added as delta not returned for " + region + " region vip address", localInstance2);
    }

    private void waitForDeltaToBeRetrieved() throws InterruptedException {
        int count = 0;
        while (count < 3 && !mockLocalEurekaServer.isSentDelta()) {
            System.out.println("Sleeping for " + CLIENT_REFRESH_RATE + " seconds to let the remote registry fetch delta. Attempt: " + count);
            Thread.sleep( 3 * CLIENT_REFRESH_RATE * 1000);
            System.out.println("Done sleeping for 10 seconds to let the remote registry fetch delta. Delta fetched: " + mockLocalEurekaServer.isSentDelta());
        }

        System.out.println("Sleeping for extra " + CLIENT_REFRESH_RATE + " seconds for the client to update delta in memory.");
    }

    private void populateRemoteRegistryAtStartup() {
        Application myapp = createRemoteApps();
        Application myappDelta = createRemoteAppsDelta();
        remoteRegionApps.put(REMOTE_REGION_APP_NAME, myapp);
        remoteRegionAppsDelta.put(REMOTE_REGION_APP_NAME, myappDelta);
    }

    private Application createRemoteApps() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private Application createRemoteAppsDelta() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_2_HOSTNAME);
        instanceInfo.setActionType(InstanceInfo.ActionType.ADDED);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private InstanceInfo createRemoteInstance(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(REMOTE_REGION_APP_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP_ADDR);
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        AmazonInfo amazonInfo = getAmazonInfo(REMOTE_ZONE, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        return instanceBuilder.build();
    }

}
