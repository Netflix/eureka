package com.netflix.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author Nitesh Kant
 */
public class InstanceRegistryTest extends AbstractTester {

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

    @Test
    public void testAppsHashCodeAfterRefresh() throws InterruptedException {
        Assert.assertEquals("UP_1_", registry.getApplicationsFromAllRemoteRegions().getAppsHashCode());

        registerInstanceLocally(createLocalInstance(LOCAL_REGION_INSTANCE_2_HOSTNAME));
        waitForDeltaToBeRetrieved();

        Assert.assertEquals("UP_2_", registry.getApplicationsFromAllRemoteRegions().getAppsHashCode());
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

}
