package com.netflix.discovery;

import javax.annotation.Nullable;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Supplier;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import com.netflix.eventbus.impl.EventBusImpl;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.governator.annotations.binding.DownStatus;
import com.netflix.governator.annotations.binding.UpStatus;
import com.netflix.governator.configuration.PropertiesConfigurationProvider;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorMode;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

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

    @Rule
    public MockRemoteEurekaServer mockLocalEurekaServer = new MockRemoteEurekaServer();

    private InstanceInfo instanceInfo;
    private static EventBus eventBus = new EventBusImpl();

    @Before
    public void setUp() throws Exception {
        final int eurekaPort = mockLocalEurekaServer.getPort();
        ConfigurationManager.getConfigInstance().setProperty("eureka.client.refresh.interval", CLIENT_REFRESH_RATE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.fetchRemoteRegionsRegistry", REMOTE_REGION);
        ConfigurationManager.getConfigInstance().setProperty("eureka.myregion.availabilityZones", REMOTE_ZONE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                "http://localhost:" + eurekaPort +
                        MockRemoteEurekaServer.EUREKA_API_BASE_PATH);
        populateLocalRegistryAtStartup();
        populateRemoteRegistryAtStartup();

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
    }


    public static class Service {
        final Supplier<Boolean> upStatusSupplier;
        final Supplier<Boolean> dnStatusSupplier;
        final EurekaClient client;
        final EurekaUpStatusResolver status;

        @Inject
        public Service(
                EurekaClient client,
                @UpStatus Supplier<Boolean> upStatusSupplier,
                @DownStatus Supplier<Boolean> dnStatusSupplier,
                EurekaUpStatusResolver status
        ) {
            this.client = client;
            this.status = status;
            this.upStatusSupplier = upStatusSupplier;
            this.dnStatusSupplier = dnStatusSupplier;

            assertState(true);
        }

        public void assertState(boolean state) {
            System.out.println("EurekaStatus update count: " + status.getChangeCount());
            System.out.println("Status: " + upStatusSupplier.get());
            System.out.println("!Status: " + dnStatusSupplier.get());
            Assert.assertEquals(state, (boolean) upStatusSupplier.get());
            Assert.assertEquals(state, !dnStatusSupplier.get());
        }
    }

    @Test
    @Ignore
    public void testStatus() throws Exception {
        Injector injector = LifecycleInjector.builder()
                .withMode(LifecycleInjectorMode.SIMULATED_CHILD_INJECTORS)
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
                                bind(InstanceInfo.class).toInstance(instanceInfo);
                            }
                        })
                .build()
                .createInjector();

        Service service = injector.getInstance(Service.class);
        mockLocalEurekaServer.waitForDeltaToBeRetrieved(CLIENT_REFRESH_RATE);
        service.assertState(true);

        DiscoveryClient client = injector.getInstance(DiscoveryClient.class);
        client.unregister();
        mockLocalEurekaServer.waitForDeltaToBeRetrieved(CLIENT_REFRESH_RATE);
        TimeUnit.SECONDS.sleep(CLIENT_REFRESH_RATE * 2);

        service.assertState(false);

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
        mockLocalEurekaServer.addLocalRegionApps(LOCAL_REGION_APP_NAME, myapp);
        mockLocalEurekaServer.addLocalRegionAppsDelta(LOCAL_REGION_APP_NAME, myappDelta);
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

    private void populateRemoteRegistryAtStartup() {
        Application myapp = createRemoteApps();
        Application myappDelta = createRemoteAppsDelta();
        mockLocalEurekaServer.addRemoteRegionApps(REMOTE_REGION_APP_NAME, myapp);
        mockLocalEurekaServer.addRemoteRegionAppsDelta(REMOTE_REGION_APP_NAME, myappDelta);
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
