package com.netflix.discovery;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author Nitesh Kant
 */
public class AbstractDiscoveryClientTester {

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

    @Rule
    public MockRemoteEurekaServer mockLocalEurekaServer= new MockRemoteEurekaServer();
    protected DiscoveryClient client;

    @Before
    public void setUp() throws Exception {

        ConfigurationManager.getConfigInstance().setProperty("eureka.shouldFetchRegistry", "true");
        ConfigurationManager.getConfigInstance().setProperty("eureka.responseCacheAutoExpirationInSeconds", "10");
        ConfigurationManager.getConfigInstance().setProperty("eureka.client.refresh.interval", CLIENT_REFRESH_RATE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.fetchRemoteRegionsRegistry", REMOTE_REGION);
        ConfigurationManager.getConfigInstance().setProperty("eureka.myregion.availabilityZones", REMOTE_ZONE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                                                             "http://localhost:" + mockLocalEurekaServer.getPort() +
                                                             MockRemoteEurekaServer.EUREKA_API_BASE_PATH);

        populateLocalRegistryAtStartup();
        populateRemoteRegistryAtStartup();

        setupDiscoveryClient();
    }

    protected void setupDiscoveryClient() {
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

        ApplicationInfoManager.getInstance().initComponent(new MyDataCenterInstanceConfig());
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.shutdown();
        }
        ConfigurationManager.getConfigInstance().clearProperty("eureka.client.refresh.interval");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.registration.enabled");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.fetchRemoteRegionsRegistry");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.myregion.availabilityZones");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.serviceUrl.default");
    }

    private void populateRemoteRegistryAtStartup() {
        for (Application app : createRemoteApps()) {
            mockLocalEurekaServer.addRemoteRegionApps(app.getName(), app);
        }

        for (Application appDelta : createRemoteAppsDelta()) {
            mockLocalEurekaServer.addRemoteRegionAppsDelta(appDelta.getName(), appDelta);
        }
    }

    private static List<Application> createRemoteApps() {
        Application myapp1 = new Application(REMOTE_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createRemoteInstanceForApp1(REMOTE_REGION_APP1_INSTANCE1_HOSTNAME);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(REMOTE_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createRemoteInstanceForApp2(REMOTE_REGION_APP2_INSTANCE1_HOSTNAME);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    private static List<Application> createRemoteAppsDelta() {
        Application myapp1 = new Application(REMOTE_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createRemoteInstanceForApp1(REMOTE_REGION_APP1_INSTANCE2_HOSTNAME);
        instanceInfo1.setActionType(InstanceInfo.ActionType.ADDED);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(REMOTE_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createRemoteInstanceForApp2(REMOTE_REGION_APP2_INSTANCE2_HOSTNAME);
        instanceInfo2.setActionType(InstanceInfo.ActionType.ADDED);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    private static InstanceInfo createRemoteInstanceForApp1(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = createBaseInstance(instanceHostName);
        instanceBuilder.setAppName(REMOTE_REGION_APP1_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP1_ADDR);
        AmazonInfo amazonInfo = getAmazonInfo(REMOTE_ZONE, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        return instanceBuilder.build();
    }

    private static InstanceInfo createRemoteInstanceForApp2(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = createBaseInstance(instanceHostName);
        instanceBuilder.setAppName(REMOTE_REGION_APP2_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP2_ADDR);
        AmazonInfo amazonInfo = getAmazonInfo(REMOTE_ZONE, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        return instanceBuilder.build();
    }

    private void populateLocalRegistryAtStartup() {
        for (Application app : createLocalApps()) {
            mockLocalEurekaServer.addLocalRegionApps(app.getName(), app);
        }

        for (Application appDelta : createLocalAppsDelta()) {
            mockLocalEurekaServer.addLocalRegionAppsDelta(appDelta.getName(), appDelta);
        }
    }

    private static List<Application> createLocalApps() {
        Application myapp1 = new Application(LOCAL_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createLocalInstanceForApp1(LOCAL_REGION_APP1_INSTANCE1_HOSTNAME);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(LOCAL_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createLocalInstanceForApp2(LOCAL_REGION_APP2_INSTANCE1_HOSTNAME);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    private static List<Application> createLocalAppsDelta() {
        Application myapp1 = new Application(LOCAL_REGION_APP1_NAME);
        InstanceInfo instanceInfo1 = createLocalInstanceForApp1(LOCAL_REGION_APP1_INSTANCE2_HOSTNAME);
        instanceInfo1.setActionType(InstanceInfo.ActionType.ADDED);
        myapp1.addInstance(instanceInfo1);

        Application myapp2 = new Application(LOCAL_REGION_APP2_NAME);
        InstanceInfo instanceInfo2 = createLocalInstanceForApp2(LOCAL_REGION_APP2_INSTANCE2_HOSTNAME);
        instanceInfo2.setActionType(InstanceInfo.ActionType.ADDED);
        myapp2.addInstance(instanceInfo2);

        return Arrays.asList(myapp1, myapp2);
    }

    private static InstanceInfo.Builder createBaseInstance(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        instanceBuilder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
        return instanceBuilder;
    }

    private static InstanceInfo createLocalInstanceForApp1(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = createBaseInstance(instanceHostName);
        instanceBuilder.setAppName(LOCAL_REGION_APP1_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP1_ADDR);
        AmazonInfo amazonInfo = getAmazonInfo(null, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        return instanceBuilder.build();
    }

    private static InstanceInfo createLocalInstanceForApp2(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = createBaseInstance(instanceHostName);
        instanceBuilder.setAppName(LOCAL_REGION_APP2_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP2_ADDR);
        AmazonInfo amazonInfo = getAmazonInfo(null, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        return instanceBuilder.build();
    }

    private static AmazonInfo getAmazonInfo(@Nullable String availabilityZone, String instanceHostName) {
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
