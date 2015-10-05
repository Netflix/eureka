package com.netflix.eureka.registry;

import java.util.List;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.Pair;
import com.netflix.eureka.AbstractTester;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Nitesh Kant
 */
public class InstanceRegistryTest extends AbstractTester {

    @Test
    public void testSoftDepRemoteUp() throws Exception {
        Assert.assertTrue("Registry access disallowed when remote region is UP.", registry.shouldAllowAccess(false));
        Assert.assertTrue("Registry access disallowed when remote region is UP.", registry.shouldAllowAccess(true));
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
        registerInstanceLocally(createLocalInstance(LOCAL_REGION_INSTANCE_2_HOSTNAME)); /// local delta
        waitForDeltaToBeRetrieved();
        Applications appDelta = registry.getApplicationDeltasFromMultipleRegions(null);
        List<Application> registeredApplications = appDelta.getRegisteredApplications();
        Assert.assertEquals("Apps size from remote regions do not match", 2, registeredApplications.size());
        Application localApplication = null;
        Application remApplication = null;
        for (Application registeredApplication : registeredApplications) {
            if (registeredApplication.getName().equalsIgnoreCase(LOCAL_REGION_APP_NAME)) {
                localApplication = registeredApplication;
            }
            if (registeredApplication.getName().equalsIgnoreCase(REMOTE_REGION_APP_NAME)) {
                remApplication = registeredApplication;
            }
        }
        Assert.assertNotNull("Did not find local registry app in delta.", localApplication);
        Assert.assertEquals("Local registry app instance count in delta not as expected.", 1,
                localApplication.getInstances().size());
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
        System.out.println("Sleeping up to 35 seconds to let the remote registry fetch delta.");
        while (count++ < 35 && !mockRemoteEurekaServer.isSentDelta()) {
            Thread.sleep(1000);
        }
        if (!mockRemoteEurekaServer.isSentDelta()) {
            System.out.println("Waited for 35 seconds but remote server did not send delta");
        }
        // Wait 2 seconds more to be sure the delta was processed
        Thread.sleep(2000);
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
        registerInstanceLocally(createRemoteInstance(LOCAL_REGION_INSTANCE_2_HOSTNAME));
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

    @Test
    public void testStatusOverrideSetAndRemoval() throws Exception {
        // Regular registration first
        InstanceInfo myInstance = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);

        // Override status
        boolean statusResult = registry.statusUpdate(LOCAL_REGION_APP_NAME, myInstance.getId(), InstanceStatus.OUT_OF_SERVICE, "0", false);
        assertThat("Couldn't override instance status", statusResult, is(true));
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Register again with status UP (this is what health check is doing)
        registry.register(createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME), 10000000, false);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Now remove override
        statusResult = registry.deleteStatusOverride(LOCAL_REGION_APP_NAME, myInstance.getId(), InstanceStatus.DOWN, "0", false);
        assertThat("Couldn't remove status override", statusResult, is(true));
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.DOWN);

        // Register again with status UP (this is what health check is doing)
        registry.register(createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME), 10000000, false);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);
    }

    @Test
    public void testEvictionTaskCompensationTime() throws Exception {
        long evictionTaskPeriodNanos = serverConfig.getEvictionIntervalTimerInMs() * 1000000;

        AbstractInstanceRegistry.EvictionTask testTask = spy(registry.new EvictionTask());

        when(testTask.getCurrentTimeNano())
                .thenReturn(1l)  // less than the period
                .thenReturn(1l + evictionTaskPeriodNanos)  // exactly 1 period
                .thenReturn(1l + evictionTaskPeriodNanos*2 + 10000000l)  // 10ms longer than 1 period
                .thenReturn(1l + evictionTaskPeriodNanos*3 - 1l);  // less than 1 period

        assertThat(testTask.getCompensationTimeMs(), is(0l));
        assertThat(testTask.getCompensationTimeMs(), is(0l));
        assertThat(testTask.getCompensationTimeMs(), is(10l));
        assertThat(testTask.getCompensationTimeMs(), is(0l));
    }

    private void verifyLocalInstanceStatus(String id, InstanceStatus status) {
        InstanceInfo instanceInfo = registry.getApplication(LOCAL_REGION_APP_NAME).getByInstanceId(id);
        assertThat("InstanceInfo with id " + id + " not found", instanceInfo, is(notNullValue()));
        assertThat("Invalid InstanceInfo state", instanceInfo.getStatus(), is(equalTo(status)));
    }

    private void registerInstanceLocally(InstanceInfo remoteInstance) {
        registry.register(remoteInstance, 10000000, false);
        registeredApps.add(new Pair<String, String>(LOCAL_REGION_APP_NAME, LOCAL_REGION_APP_NAME));
    }

}
