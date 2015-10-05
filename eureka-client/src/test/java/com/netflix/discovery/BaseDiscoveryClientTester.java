package com.netflix.discovery;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import com.netflix.discovery.shared.Application;
import org.junit.Rule;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * @author Nitesh Kant
 */
public abstract class BaseDiscoveryClientTester {

    public static final String REMOTE_REGION = "myregion";
    public static final String REMOTE_ZONE = "myzone";
    public static final int CLIENT_REFRESH_RATE = 10;

    public static final String ALL_REGIONS_VIP1_ADDR = "myvip1";
    public static final String REMOTE_REGION_APP1_INSTANCE1_HOSTNAME = "blah1-1";
    public static final String REMOTE_REGION_APP1_INSTANCE2_HOSTNAME = "blah1-2";
    public static final String LOCAL_REGION_APP1_NAME = "MYAPP1_LOC";
    public static final String LOCAL_REGION_APP1_INSTANCE1_HOSTNAME = "blahloc1-1";
    public static final String LOCAL_REGION_APP1_INSTANCE2_HOSTNAME = "blahloc1-2";
    public static final String REMOTE_REGION_APP1_NAME = "MYAPP1";

    public static final String ALL_REGIONS_VIP2_ADDR = "myvip2";
    public static final String REMOTE_REGION_APP2_INSTANCE1_HOSTNAME = "blah2-1";
    public static final String REMOTE_REGION_APP2_INSTANCE2_HOSTNAME = "blah2-2";
    public static final String LOCAL_REGION_APP2_NAME = "MYAPP2_LOC";
    public static final String LOCAL_REGION_APP2_INSTANCE1_HOSTNAME = "blahloc2-1";
    public static final String LOCAL_REGION_APP2_INSTANCE2_HOSTNAME = "blahloc2-2";
    public static final String REMOTE_REGION_APP2_NAME = "MYAPP2";

    public static final String ALL_REGIONS_VIP3_ADDR = "myvip3";
    public static final String LOCAL_REGION_APP3_NAME = "MYAPP3_LOC";
    public static final String LOCAL_REGION_APP3_INSTANCE1_HOSTNAME = "blahloc3-1";

    @Rule
    public MockRemoteEurekaServer mockLocalEurekaServer = new MockRemoteEurekaServer();
    protected EurekaClient client;

    protected void setupProperties() {
        DiscoveryClientResource.setupDiscoveryClientConfig(mockLocalEurekaServer.getPort(),
                MockRemoteEurekaServer.EUREKA_API_BASE_PATH);
    }

    protected void setupDiscoveryClient() {
        setupDiscoveryClient(30);
    }

    protected void setupDiscoveryClient(int renewalIntervalInSecs) {
        InstanceInfo instanceInfo = newInstanceInfoBuilder(renewalIntervalInSecs).build();
        client = DiscoveryClientResource.setupDiscoveryClient(instanceInfo);
    }

    protected InstanceInfo.Builder newInstanceInfoBuilder(int renewalIntervalInSecs) {
        return DiscoveryClientResource.newInstanceInfoBuilder(renewalIntervalInSecs);
    }

    protected void shutdownDiscoveryClient() {
        if (client != null) {
            client.shutdown();
        }
    }

    protected void addLocalAppDelta() {
        Application myappDelta = new Application(LOCAL_REGION_APP3_NAME);
        InstanceInfo instanceInfo = createInstance(LOCAL_REGION_APP3_NAME, ALL_REGIONS_VIP3_ADDR,
                LOCAL_REGION_APP3_INSTANCE1_HOSTNAME, null);
        instanceInfo.setActionType(InstanceInfo.ActionType.ADDED);
        myappDelta.addInstance(instanceInfo);
        mockLocalEurekaServer.addLocalRegionAppsDelta(LOCAL_REGION_APP3_NAME, myappDelta);
    }

    protected void populateLocalRegistryAtStartup() {
        for (Application app : createLocalApps()) {
            mockLocalEurekaServer.addLocalRegionApps(app.getName(), app);
        }

        for (Application appDelta : createLocalAppsDelta()) {
            mockLocalEurekaServer.addLocalRegionAppsDelta(appDelta.getName(), appDelta);
        }
    }

    protected void populateRemoteRegistryAtStartup() {
        for (Application app : createRemoteApps()) {
            mockLocalEurekaServer.addRemoteRegionApps(app.getName(), app);
        }

        for (Application appDelta : createRemoteAppsDelta()) {
            mockLocalEurekaServer.addRemoteRegionAppsDelta(appDelta.getName(), appDelta);
        }
    }

    protected static List<Application> createRemoteApps() {
        Application myapp1 = new Application(REMOTE_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createInstance(REMOTE_REGION_APP1_NAME, ALL_REGIONS_VIP1_ADDR,
                REMOTE_REGION_APP1_INSTANCE1_HOSTNAME, REMOTE_ZONE);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(REMOTE_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createInstance(REMOTE_REGION_APP2_NAME, ALL_REGIONS_VIP2_ADDR,
                REMOTE_REGION_APP2_INSTANCE1_HOSTNAME, REMOTE_ZONE);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    protected static List<Application> createRemoteAppsDelta() {
        Application myapp1 = new Application(REMOTE_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createInstance(REMOTE_REGION_APP1_NAME, ALL_REGIONS_VIP1_ADDR,
                REMOTE_REGION_APP1_INSTANCE2_HOSTNAME, REMOTE_ZONE);
        instanceInfo1.setActionType(InstanceInfo.ActionType.ADDED);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(REMOTE_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createInstance(REMOTE_REGION_APP2_NAME, ALL_REGIONS_VIP2_ADDR,
                REMOTE_REGION_APP2_INSTANCE2_HOSTNAME, REMOTE_ZONE);
        instanceInfo2.setActionType(InstanceInfo.ActionType.ADDED);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    protected static List<Application> createLocalApps() {
        Application myapp1 = new Application(LOCAL_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createInstance(LOCAL_REGION_APP1_NAME, ALL_REGIONS_VIP1_ADDR,
                LOCAL_REGION_APP1_INSTANCE1_HOSTNAME, null);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(LOCAL_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createInstance(LOCAL_REGION_APP2_NAME, ALL_REGIONS_VIP2_ADDR,
                LOCAL_REGION_APP2_INSTANCE1_HOSTNAME, null);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    protected static List<Application> createLocalAppsDelta() {
        Application myapp1 = new Application(LOCAL_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createInstance(LOCAL_REGION_APP1_NAME, ALL_REGIONS_VIP1_ADDR,
                LOCAL_REGION_APP1_INSTANCE2_HOSTNAME, null);
        instanceInfo1.setActionType(InstanceInfo.ActionType.ADDED);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(LOCAL_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createInstance(LOCAL_REGION_APP2_NAME, ALL_REGIONS_VIP2_ADDR,
                LOCAL_REGION_APP2_INSTANCE2_HOSTNAME, null);
        instanceInfo2.setActionType(InstanceInfo.ActionType.ADDED);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    protected static InstanceInfo createInstance(String appName, String vipAddress, String instanceHostName, String zone) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(appName);
        instanceBuilder.setVIPAddress(vipAddress);
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        AmazonInfo amazonInfo = getAmazonInfo(zone, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        instanceBuilder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
        return instanceBuilder.build();
    }

    protected static AmazonInfo getAmazonInfo(@Nullable String availabilityZone, String instanceHostName) {
        AmazonInfo.Builder azBuilder = AmazonInfo.Builder.newBuilder();
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.availabilityZone, null == availabilityZone ? "us-east-1a" : availabilityZone);
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.instanceId, instanceHostName);
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.amiId, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.instanceType, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.localIpv4, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.publicIpv4, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.publicHostname, instanceHostName);
        return azBuilder.build();
    }
}
