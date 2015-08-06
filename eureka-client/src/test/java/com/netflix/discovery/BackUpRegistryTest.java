package com.netflix.discovery;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

import com.google.inject.util.Providers;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Nitesh Kant
 */
public class BackUpRegistryTest {


    public static final String ALL_REGIONS_VIP_ADDR = "myvip";
    public static final String REMOTE_REGION_INSTANCE_1_HOSTNAME = "blah";
    public static final String REMOTE_REGION_INSTANCE_2_HOSTNAME = "blah2";

    public static final String LOCAL_REGION_APP_NAME = "MYAPP_LOC";
    public static final String LOCAL_REGION_INSTANCE_1_HOSTNAME = "blahloc";
    public static final String LOCAL_REGION_INSTANCE_2_HOSTNAME = "blahloc2";

    public static final String REMOTE_REGION_APP_NAME = "MYAPP";
    public static final String REMOTE_REGION = "myregion";
    public static final String REMOTE_ZONE = "myzone";
    public static final int CLIENT_REFRESH_RATE = 10;
    public static final int NOT_AVAILABLE_EUREKA_PORT = 756473;

    private EurekaClient client;
    private MockBackupRegistry backupRegistry;

    public void setUp(boolean enableRemote) throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("eureka.client.refresh.interval", CLIENT_REFRESH_RATE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        if (enableRemote) {
            ConfigurationManager.getConfigInstance().setProperty("eureka.fetchRemoteRegionsRegistry", REMOTE_REGION);
        }
        ConfigurationManager.getConfigInstance().setProperty("eureka.myregion.availabilityZones", REMOTE_ZONE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.backupregistry", MockBackupRegistry.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                "http://localhost:" + NOT_AVAILABLE_EUREKA_PORT /*Should always be unavailable*/
                        +
                        MockRemoteEurekaServer.EUREKA_API_BASE_PATH);

        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
        builder.setIPAddr("10.10.101.00");
        builder.setHostName("Hosttt");
        builder.setAppName("EurekaTestApp-" + UUID.randomUUID());
        builder.setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });

        ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(new MyDataCenterInstanceConfig(), builder.build());

        backupRegistry = new MockBackupRegistry();
        setupBackupMock();
        client = new DiscoveryClient(
                applicationInfoManager,
                new DefaultEurekaClientConfig(),
                null,
                Providers.of((BackupRegistry)backupRegistry)
        );
    }

    @After
    public void tearDown() throws Exception {
        client.shutdown();
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void testLocalOnly() throws Exception {
        setUp(false);
        Applications applications = client.getApplications();
        List<Application> registeredApplications = applications.getRegisteredApplications();
        System.out.println("***" + registeredApplications);
        Assert.assertNotNull("Local region apps not found.", registeredApplications);
        Assert.assertEquals("Local apps size not as expected.", 1, registeredApplications.size());
        Assert.assertEquals("Local region apps not present.", LOCAL_REGION_APP_NAME, registeredApplications.get(0).getName());
    }

    @Test
    public void testRemoteEnabledButLocalOnlyQueried() throws Exception {
        setUp(true);
        Applications applications = client.getApplications();
        List<Application> registeredApplications = applications.getRegisteredApplications();
        Assert.assertNotNull("Local region apps not found.", registeredApplications);
        Assert.assertEquals("Local apps size not as expected.", 2, registeredApplications.size()); // Remote region comes with no instances.
        Application localRegionApp = null;
        Application remoteRegionApp = null;
        for (Application registeredApplication : registeredApplications) {
            if (registeredApplication.getName().equals(LOCAL_REGION_APP_NAME)) {
                localRegionApp = registeredApplication;
            } else if (registeredApplication.getName().equals(REMOTE_REGION_APP_NAME)) {
                remoteRegionApp = registeredApplication;
            }
        }
        Assert.assertNotNull("Local region apps not present.", localRegionApp);
        Assert.assertTrue("Remote region instances returned for local query.", null == remoteRegionApp || remoteRegionApp.getInstances().isEmpty());
    }

    @Test
    public void testRemoteEnabledAndQueried() throws Exception {
        setUp(true);
        Applications applications = client.getApplicationsForARegion(REMOTE_REGION);
        List<Application> registeredApplications = applications.getRegisteredApplications();
        Assert.assertNotNull("Remote region apps not found.", registeredApplications);
        Assert.assertEquals("Remote apps size not as expected.", 1, registeredApplications.size());
        Assert.assertEquals("Remote region apps not present.", REMOTE_REGION_APP_NAME, registeredApplications.get(0).getName());
    }

    @Test
    public void testAppsHashCode() throws Exception {
        setUp(true);
        Applications applications = client.getApplications();

        Assert.assertEquals("UP_1_", applications.getAppsHashCode());
    }

    private void setupBackupMock() {
        Application localApp = createLocalApps();
        Applications localApps = new Applications();
        localApps.addApplication(localApp);
        backupRegistry.setLocalRegionApps(localApps);
        Application remoteApp = createRemoteApps();
        Applications remoteApps = new Applications();
        remoteApps.addApplication(remoteApp);
        backupRegistry.getRemoteRegionVsApps().put(REMOTE_REGION, remoteApps);
    }

    private Application createLocalApps() {
        Application myapp = new Application(LOCAL_REGION_APP_NAME);
        InstanceInfo instanceInfo = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }


    private Application createRemoteApps() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_1_HOSTNAME);
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
}
