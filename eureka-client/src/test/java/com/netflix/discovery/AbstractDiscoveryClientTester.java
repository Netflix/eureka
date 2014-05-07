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
import java.util.UUID;

/**
 * @author Nitesh Kant
 */
public class AbstractDiscoveryClientTester {

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
        Application myapp = createRemoteApps();
        Application myappDelta = createRemoteAppsDelta();

        mockLocalEurekaServer.addRemoteRegionApps(REMOTE_REGION_APP_NAME, myapp);
        mockLocalEurekaServer.addRemoteRegionAppsDelta(REMOTE_REGION_APP_NAME, myappDelta);
    }

    private static Application createRemoteApps() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private static Application createRemoteAppsDelta() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_2_HOSTNAME);
        instanceInfo.setActionType(InstanceInfo.ActionType.ADDED);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private static InstanceInfo createRemoteInstance(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(REMOTE_REGION_APP_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP_ADDR);
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        AmazonInfo amazonInfo = getAmazonInfo(REMOTE_ZONE, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        instanceBuilder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
        return instanceBuilder.build();
    }

    private void populateLocalRegistryAtStartup() {
        Application myapp = createLocalApps();
        Application myappDelta = createLocalAppsDelta();
        mockLocalEurekaServer.addLocalRegionApps(LOCAL_REGION_APP_NAME, myapp);
        mockLocalEurekaServer.addLocalRegionAppsDelta(LOCAL_REGION_APP_NAME, myappDelta);
    }

    private static Application createLocalApps() {
        Application myapp = new Application(LOCAL_REGION_APP_NAME);
        InstanceInfo instanceInfo = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private static Application createLocalAppsDelta() {
        Application myapp = new Application(LOCAL_REGION_APP_NAME);
        InstanceInfo instanceInfo = createLocalInstance(LOCAL_REGION_INSTANCE_2_HOSTNAME);
        instanceInfo.setActionType(InstanceInfo.ActionType.ADDED);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private static InstanceInfo createLocalInstance(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(LOCAL_REGION_APP_NAME);
        instanceBuilder.setVIPAddress(ALL_REGIONS_VIP_ADDR);
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        AmazonInfo amazonInfo = getAmazonInfo(null, instanceHostName);
        instanceBuilder.setDataCenterInfo(amazonInfo);
        instanceBuilder.setMetadata(amazonInfo.getMetadata());
        instanceBuilder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
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
