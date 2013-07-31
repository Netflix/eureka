package com.netflix.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.Pair;
import com.netflix.eureka.mock.MockRemoteEurekaServer;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Nitesh Kant
 */
public class InstanceRegistryTest {

    public static final int REMOTE_REGION_PORT = 7777;
    public static final String REMOTE_REGION_NAME = "myregion";
    private final Map<String, Application> remoteRegionApps = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionAppsDelta = new HashMap<String, Application>();

    private List<Pair<String, String>> registeredApps = new ArrayList<Pair<String, String>>();
    private MockRemoteEurekaServer mockRemoteEurekaServer;
    private InstanceRegistry registry;
    public static final String REMOTE_REGION_APP_NAME = "MYAPP";
    public static final String REMOTE_REGION_INSTANCE_1_HOSTNAME = "blah";

    public static final String LOCAL_REGION_APP_NAME = "MYLOCAPP";
    public static final String LOCAL_REGION_INSTANCE_1_HOSTNAME = "blahloc";
    public static final String LOCAL_REGION_INSTANCE_2_HOSTNAME = "blahloc2";

    @Before
    public void setUp() throws Exception {
        ConfigurationManager.getConfigInstance().clearProperty("eureka.remoteRegion.global.appWhiteList");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.remoteRegion." + REMOTE_REGION_NAME + ".appWhiteList");
        ConfigurationManager.getConfigInstance().setProperty("eureka.deltaRetentionTimerIntervalInMs", "600000");
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.registryFetchIntervalInSeconds",
                                                             "5");
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegionUrlsWithName",
                                                             REMOTE_REGION_NAME + ";http://localhost:" + REMOTE_REGION_PORT + "/" +
                                                             MockRemoteEurekaServer.EUREKA_API_BASE_PATH);
        populateRemoteRegistryAtStartup();
        mockRemoteEurekaServer = new MockRemoteEurekaServer(REMOTE_REGION_PORT, remoteRegionApps,
                                                            remoteRegionAppsDelta);
        mockRemoteEurekaServer.start();

        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        ApplicationInfoManager.getInstance().initComponent(new MyDataCenterInstanceConfig());
        registry = new InstanceRegistry() {

            @Override
            public boolean isLeaseExpirationEnabled() {
                return false;
            }

            @Override
            public InstanceInfo getNextServerFromEureka(String virtualHostname, boolean secure) {
                return null;
            }
        };
        registry.initRemoteRegionRegistry();
    }

    @After
    public void tearDown() throws Exception {
        mockRemoteEurekaServer.stop();
        for (Pair<String, String> registeredApp : registeredApps) {
            System.out.println("Canceling application: " + registeredApp.first() + " from local registry.");
            registry.cancel(registeredApp.first(), registeredApp.second(), false);
        }
        remoteRegionApps.clear();
        remoteRegionAppsDelta.clear();
        ConfigurationManager.getConfigInstance().clearProperty("eureka.remoteRegionUrls");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.deltaRetentionTimerIntervalInMs");
    }

    @Test
    public void testGetAppsFromAllRemoteRegions() throws Exception {
        Applications apps = registry.getApplicationsFromAllRemoteRegions();
        List<Application> registeredApplications = apps.getRegisteredApplications();
        Assert.assertEquals("Apps size from remote regions do not match", 1, registeredApplications.size());
        Application app = registeredApplications.iterator().next();
        Assert.assertEquals("Added app did not return from remote registry", REMOTE_REGION_APP_NAME, app.getName());
        Assert.assertEquals("Returned app did not have the instance", 1, app.getInstances().size());
    }

    @Test
    public void testGetAppsDeltaFromAllRemoteRegions() throws Exception {
        testGetAppsFromAllRemoteRegions(); // to add to registry

        registerInstanceLocally(createLocalInstance(LOCAL_REGION_INSTANCE_2_HOSTNAME)); /// local delta
        waitForDeltaToBeRetrieved();
        Applications appDelta = registry.getApplicationDeltasFromMultipleRegions(null);
        List<Application> registeredApplications = appDelta.getRegisteredApplications();
        Assert.assertEquals("Apps size from remote regions do not match", 2, registeredApplications.size());
        Application locaApplication = null;
        Application remApplication = null;
        for (Application registeredApplication : registeredApplications) {
            if (registeredApplication.getName().equalsIgnoreCase(LOCAL_REGION_APP_NAME)) {
                locaApplication = registeredApplication;
            }
            if (registeredApplication.getName().equalsIgnoreCase(REMOTE_REGION_APP_NAME)) {
                remApplication = registeredApplication;
            }
        }
        Assert.assertNotNull("Did not find local registry app in delta.", locaApplication);
        Assert.assertEquals("Local registry app instance count in delta not as expected.", 1,
                            locaApplication.getInstances().size());
        Assert.assertNotNull("Did not find remote registry app in delta", remApplication);
        Assert.assertEquals("Remote registry app instance count  in delta not as expected.", 1,
                            remApplication.getInstances().size());
    }

    private void waitForDeltaToBeRetrieved() throws InterruptedException {
        int count = 0;
        while (count < 3 && !mockRemoteEurekaServer.isSentDelta()) {
            System.out.println("Sleeping for 10 seconds to let the remote registry fetch delta. Attempt: " + count);
            Thread.sleep(10 * 1000);
            System.out.println("Done sleeping for 10 seconds to let the remote registry fetch delta");
        }
    }

    @Test
    public void testGetAppsFromLocalRegionOnly() throws Exception {
        registerInstanceLocally(createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME));

        Applications apps = registry.getApplicationsFromLocalRegionOnly();
        List<Application> registeredApplications = apps.getRegisteredApplications();
        Assert.assertEquals("Apps size from local region do not match", 1, registeredApplications.size());
        Application app = registeredApplications.iterator().next();
        Assert.assertEquals("Added app did not return from local registry", LOCAL_REGION_APP_NAME, app.getName());
        Assert.assertEquals("Returned app did not have the instance", 1, app.getInstances().size());
    }

    @Test
    public void testGetAppsFromBothRegions() throws Exception {
        registerInstanceLocally(createRemoteInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME));
        registerInstanceLocally(createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME));

        Applications apps = registry.getApplicationsFromAllRemoteRegions();
        List<Application> registeredApplications = apps.getRegisteredApplications();
        Assert.assertEquals("Apps size from both regions do not match", 2, registeredApplications.size());
        Application locaApplication = null;
        Application remApplication = null;
        for (Application registeredApplication : registeredApplications) {
            if (registeredApplication.getName().equalsIgnoreCase(LOCAL_REGION_APP_NAME)) {
                locaApplication = registeredApplication;
            }
            if (registeredApplication.getName().equalsIgnoreCase(REMOTE_REGION_APP_NAME)) {
                remApplication = registeredApplication;
            }
        }
        Assert.assertNotNull("Did not find local registry app", locaApplication);
        Assert.assertEquals("Local registry app instance count not as expected.", 1,
                            locaApplication.getInstances().size());
        Assert.assertNotNull("Did not find remote registry app", remApplication);
        Assert.assertEquals("Remote registry app instance count not as expected.", 2,
                            remApplication.getInstances().size());

    }

    private void registerInstanceLocally(InstanceInfo remoteInstance) {
        registry.register(remoteInstance, 10000000, false);
        registeredApps.add(new Pair<String, String>(LOCAL_REGION_APP_NAME, LOCAL_REGION_APP_NAME));
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
        //instanceInfo.setActionType(InstanceInfo.ActionType.MODIFIED);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private Application createRemoteAppsDelta() {
        Application myapp = new Application(REMOTE_REGION_APP_NAME);
        InstanceInfo instanceInfo = createRemoteInstance(REMOTE_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private InstanceInfo createRemoteInstance(String instanceHostName) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(REMOTE_REGION_APP_NAME);
        instanceBuilder.setHostName(instanceHostName);
        instanceBuilder.setIPAddr("10.10.101.1");
        instanceBuilder.setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });
        return instanceBuilder.build();
    }

    private Application createLocalApps() {
        Application myapp = new Application(LOCAL_REGION_APP_NAME);
        InstanceInfo instanceInfo = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        myapp.addInstance(instanceInfo);
        return myapp;
    }

    private InstanceInfo createLocalInstance(String hostname) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(LOCAL_REGION_APP_NAME);
        instanceBuilder.setHostName(hostname);
        instanceBuilder.setIPAddr("10.10.101.1");
        instanceBuilder.setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });
        return instanceBuilder.build();
    }
}
