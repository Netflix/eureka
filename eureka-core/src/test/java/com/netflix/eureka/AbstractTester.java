package com.netflix.eureka;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.blitz4j.LoggingConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Pair;
import com.netflix.eureka.mock.MockRemoteEurekaServer;
import org.junit.After;
import org.junit.Before;

/**
 * @author Nitesh Kant
 */
public class AbstractTester {

    static {
        LoggingConfiguration.getInstance().configure();
    }

    public static final String REMOTE_REGION_NAME = "us-east-1";
    public static final String REMOTE_REGION_APP_NAME = "MYAPP";
    public static final String REMOTE_REGION_INSTANCE_1_HOSTNAME = "blah";
    public static final String REMOTE_REGION_INSTANCE_2_HOSTNAME = "blah2";
    public static final String LOCAL_REGION_APP_NAME = "MYLOCAPP";
    public static final String LOCAL_REGION_INSTANCE_1_HOSTNAME = "blahloc";
    public static final String LOCAL_REGION_INSTANCE_2_HOSTNAME = "blahloc2";
    protected final List<Pair<String, String>> registeredApps = new ArrayList<Pair<String, String>>();
    protected final Map<String, Application> remoteRegionApps = new HashMap<String, Application>();
    protected final Map<String, Application> remoteRegionAppsDelta = new HashMap<String, Application>();
    protected MockRemoteEurekaServer mockRemoteEurekaServer;
    protected PeerAwareInstanceRegistry registry;
    protected EurekaClient client;
    public static final String REMOTE_ZONE = "us-east-1c";

    @Before
    public void setUp() throws Exception {
        ConfigurationManager.getConfigInstance().clearProperty("eureka.remoteRegion.global.appWhiteList");
        ConfigurationManager.getConfigInstance().setProperty("eureka.responseCacheAutoExpirationInSeconds", "10");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.remoteRegion." + REMOTE_REGION_NAME + ".appWhiteList");
        ConfigurationManager.getConfigInstance().setProperty("eureka.deltaRetentionTimerIntervalInMs", "600000");
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.registryFetchIntervalInSeconds", "5");
        populateRemoteRegistryAtStartup();
        mockRemoteEurekaServer = newMockRemoteServer();
        mockRemoteEurekaServer.start();

        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegionUrlsWithName",
                REMOTE_REGION_NAME + ";http://localhost:" + mockRemoteEurekaServer.getPort() + MockRemoteEurekaServer.EUREKA_API_BASE_PATH);

        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
        builder.setIPAddr("10.10.101.00");
        builder.setHostName("Hosttt");
        builder.setAppName("EurekaTestApp-" + UUID.randomUUID());
        builder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
        builder.setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });

        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                "http://localhost:" + mockRemoteEurekaServer.getPort() +
                        MockRemoteEurekaServer.EUREKA_API_BASE_PATH);

        DefaultEurekaClientConfig config = new DefaultEurekaClientConfig();
        // setup config in advance, used in initialize converter
        ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(new MyDataCenterInstanceConfig(), builder.build());
        DiscoveryManager.getInstance().setEurekaClientConfig(config);
        client = new DiscoveryClient(applicationInfoManager, config);
        registry = new TestPeerAwareInstanceRegistry();
        registry.initRemoteRegionRegistry();
    }

    protected MockRemoteEurekaServer newMockRemoteServer() {
        return new MockRemoteEurekaServer(0 /* use ephemeral */, remoteRegionApps, remoteRegionAppsDelta);
    }

    @After
    public void tearDown() throws Exception {
        for (Pair<String, String> registeredApp : registeredApps) {
            System.out.println("Canceling application: " + registeredApp.first() + " from local registry.");
            registry.cancel(registeredApp.first(), registeredApp.second(), false);
        }
        registry.shutdown();
        mockRemoteEurekaServer.stop();
        remoteRegionApps.clear();
        remoteRegionAppsDelta.clear();
        ConfigurationManager.getConfigInstance().clearProperty("eureka.remoteRegionUrls");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.deltaRetentionTimerIntervalInMs");
    }

    private static Application createRemoteApps() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_1_HOSTNAME);
        //instanceInfo.setActionType(InstanceInfo.ActionType.MODIFIED);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private static Application createRemoteAppsDelta() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    protected static InstanceInfo createRemoteInstance(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(REMOTE_REGION_APP_NAME);
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        instanceBuilder.setDataCenterInfo(getAmazonInfo(REMOTE_ZONE, instanceHostName));
        instanceBuilder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
        return instanceBuilder.build();
    }

    protected static InstanceInfo createLocalInstance(String hostname) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(LOCAL_REGION_APP_NAME);
        instanceBuilder.setHostName(hostname);
        instanceBuilder.setIPAddr("10.10.101.1");
        instanceBuilder.setDataCenterInfo(getAmazonInfo(null, hostname));
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

    private void populateRemoteRegistryAtStartup() {
        Application myapp = createRemoteApps();
        Application myappDelta = createRemoteAppsDelta();
        remoteRegionApps.put(REMOTE_REGION_APP_NAME, myapp);
        remoteRegionAppsDelta.put(REMOTE_REGION_APP_NAME, myappDelta);
    }

    private static class TestPeerAwareInstanceRegistry extends PeerAwareInstanceRegistry {

        @Override
        public boolean isLeaseExpirationEnabled() {
            return false;
        }

        @Override
        public InstanceInfo getNextServerFromEureka(String virtualHostname, boolean secure) {
            return null;
        }
    }
}
