package com.netflix.discovery;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nitesh Kant
 */
public class DiscoveryClientTest {

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

    private MockRemoteEurekaServer mockLocalEurekaServer;
    private final Map<String, Application> localRegionApps = new HashMap<String, Application>();
    private final Map<String, Application> localRegionAppsDelta = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionApps = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionAppsDelta = new HashMap<String, Application>();

    private DiscoveryClient client;
    private int localRandomEurekaPort = 7799;

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
        builder.setHostName("Hosttt");
        builder.setAppName("EurekaTestApp-" + UUID.randomUUID());
        builder.setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });
        client = new DiscoveryClient(builder.build(), new DefaultEurekaClientConfig());
    }

    @After
    public void tearDown() throws Exception {
        client.shutdown();
        ConfigurationManager.getConfigInstance().clearProperty("eureka.client.refresh.interval");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.registration.enabled");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.fetchRemoteRegionsRegistry");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.myregion.availabilityZones");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.serviceUrl.default");
        mockLocalEurekaServer.stop();
        localRegionApps.clear();
        localRegionAppsDelta.clear();
        remoteRegionApps.clear();
        remoteRegionAppsDelta.clear();
    }

    @Test
    public void testGetByVipInLocalRegion() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false);
        Assert.assertEquals("Unexpected number of instances found for local region.", 1, instancesByVipAddress.size());
        InstanceInfo instance = instancesByVipAddress.iterator().next();
        Assert.assertEquals("Local instance not returned for local region vip address",
                            LOCAL_REGION_INSTANCE_1_HOSTNAME, instance.getHostName());
    }

    @Test
    public void testGetAllKnownRegions() throws Exception {
        Set<String> allKnownRegions = client.getAllKnownRegions();
        Assert.assertEquals("Unexpected number of known regions.", 2, allKnownRegions.size());
        Assert.assertTrue("Remote region not found in set of known regions.", allKnownRegions.contains(REMOTE_REGION));
    }
    @Test
    public void testAllAppsForRegions() throws Exception {
        Applications appsForRemoteRegion = client.getApplicationsForARegion(REMOTE_REGION);
        Assert.assertTrue("No apps for remote region found.", (null != appsForRemoteRegion) && !appsForRemoteRegion.getRegisteredApplications().isEmpty());
        Applications appsForLocalRegion = client.getApplicationsForARegion("us-east-1");
        Assert.assertTrue("No apps for local region found.", (null != appsForLocalRegion) && !appsForLocalRegion.getRegisteredApplications().isEmpty());
    }

    @Test
    public void testGetInvalidVIP() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress("XYZ", false);
        Assert.assertEquals("Unexpected number of instances found for local region.", 0, instancesByVipAddress.size());
    }

    @Test
    public void testGetInvalidVIPForRemoteRegion() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress("XYZ", false, REMOTE_REGION);
        Assert.assertEquals("Unexpected number of instances found for local region.", 0, instancesByVipAddress.size());
    }

    @Test
    public void testGetByVipInRemoteRegion() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false, REMOTE_REGION);
        Assert.assertEquals("Unexpected number of instances found for remote region.", 1, instancesByVipAddress.size());
        InstanceInfo instance = instancesByVipAddress.iterator().next();
        Assert.assertEquals("Remote instance not returned for remote region vip address", REMOTE_REGION_INSTANCE_1_HOSTNAME, instance.getHostName());
    }

    @Test
    public void testDelta() throws Exception {
        waitForDeltaToBeRetrieved();

        checkInstancesFromARegion("local", LOCAL_REGION_INSTANCE_1_HOSTNAME,
                                  LOCAL_REGION_INSTANCE_2_HOSTNAME);
        checkInstancesFromARegion(REMOTE_REGION, REMOTE_REGION_INSTANCE_1_HOSTNAME,
                                  REMOTE_REGION_INSTANCE_2_HOSTNAME);
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
}
