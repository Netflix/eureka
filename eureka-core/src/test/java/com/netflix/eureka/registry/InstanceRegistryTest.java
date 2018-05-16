package com.netflix.eureka.registry;

import java.util.List;

import com.google.common.base.Preconditions;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.AbstractTester;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.test.async.executor.AsyncResult;
import com.netflix.eureka.test.async.executor.AsyncSequentialExecutor;
import com.netflix.eureka.test.async.executor.SequentialEvents;
import com.netflix.eureka.test.async.executor.SingleEvent;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
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
        InstanceInfo seed = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        seed.setLastDirtyTimestamp(100l);

        // Regular registration first
        InstanceInfo myInstance1 = new InstanceInfo(seed);
        registerInstanceLocally(myInstance1);
        verifyLocalInstanceStatus(myInstance1.getId(), InstanceStatus.UP);

        // Override status
        boolean statusResult = registry.statusUpdate(LOCAL_REGION_APP_NAME, seed.getId(), InstanceStatus.OUT_OF_SERVICE, "0", false);
        assertThat("Couldn't override instance status", statusResult, is(true));
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Register again with status UP to verify that the override is still in place even if the dirtytimestamp is higher
        InstanceInfo myInstance2 = new InstanceInfo(seed);  // clone to avoid object state in this test
        myInstance2.setLastDirtyTimestamp(200l);  // use a later 'client side' dirty timestamp
        registry.register(myInstance2, 10000000, false);
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Now remove override
        statusResult = registry.deleteStatusOverride(LOCAL_REGION_APP_NAME, seed.getId(), InstanceStatus.DOWN, "0", false);
        assertThat("Couldn't remove status override", statusResult, is(true));
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.DOWN);

        // Register again with status UP after the override deletion, keeping myInstance2's dirtyTimestamp (== no client side change)
        InstanceInfo myInstance3 = new InstanceInfo(seed);  // clone to avoid object state in this test
        myInstance3.setLastDirtyTimestamp(200l);  // use a later 'client side' dirty timestamp
        registry.register(myInstance3, 10000000, false);
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.UP);
    }

    @Test
    public void testStatusOverrideWithRenewAppliedToAReplica() throws Exception {
        InstanceInfo seed = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        seed.setLastDirtyTimestamp(100l);

        // Regular registration first
        InstanceInfo myInstance1 = new InstanceInfo(seed);
        registerInstanceLocally(myInstance1);
        verifyLocalInstanceStatus(myInstance1.getId(), InstanceStatus.UP);

        // Override status
        boolean statusResult = registry.statusUpdate(LOCAL_REGION_APP_NAME, seed.getId(), InstanceStatus.OUT_OF_SERVICE, "0", false);
        assertThat("Couldn't override instance status", statusResult, is(true));
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Send a renew to ensure timestamps are consistent even with the override in existence.
        // To do this, we get hold of the registry local InstanceInfo and reset its status to before an override
        // has been applied
        //  (this is to simulate a case in a replica server where the override has been replicated, but not yet
        //   applied to the local InstanceInfo)
        InstanceInfo registeredInstance = registry.getInstanceByAppAndId(seed.getAppName(), seed.getId());
        registeredInstance.setStatusWithoutDirty(InstanceStatus.UP);
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.UP);
        registry.renew(seed.getAppName(), seed.getId(), false);
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Now remove override
        statusResult = registry.deleteStatusOverride(LOCAL_REGION_APP_NAME, seed.getId(), InstanceStatus.DOWN, "0", false);
        assertThat("Couldn't remove status override", statusResult, is(true));
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.DOWN);

        // Register again with status UP after the override deletion, keeping myInstance2's dirtyTimestamp (== no client side change)
        InstanceInfo myInstance3 = new InstanceInfo(seed);  // clone to avoid object state in this test
        myInstance3.setLastDirtyTimestamp(200l);  // use a later 'client side' dirty timestamp
        registry.register(myInstance3, 10000000, false);
        verifyLocalInstanceStatus(seed.getId(), InstanceStatus.UP);
    }

    @Test
    public void testStatusOverrideStartingStatus() throws Exception {
        // Regular registration first
        InstanceInfo myInstance = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);

        // Override status
        boolean statusResult = registry.statusUpdate(LOCAL_REGION_APP_NAME, myInstance.getId(), InstanceStatus.OUT_OF_SERVICE, "0", false);
        assertThat("Couldn't override instance status", statusResult, is(true));
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.OUT_OF_SERVICE);

        // If we are not UP or OUT_OF_SERVICE, the OUT_OF_SERVICE override does not apply. It gets trumped by the current
        // status (STARTING or DOWN). Here we test with STARTING.
        myInstance = createLocalStartingInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.STARTING);
    }

    @Test
    public void testStatusOverrideWithExistingLeaseUp() throws Exception {
        // Without an override we expect to get the existing UP lease when we re-register with OUT_OF_SERVICE.
        // First, we are "up".
        InstanceInfo myInstance = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);

        // Then, we re-register with "out of service".
        InstanceInfo sameInstance = createLocalOutOfServiceInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registry.register(sameInstance, 10000000, false);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);

        // Let's try again. We shouldn't see a difference.
        sameInstance = createLocalOutOfServiceInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registry.register(sameInstance, 10000000, false);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);
    }

    @Test
    public void testStatusOverrideWithExistingLeaseOutOfService() throws Exception {
        // Without an override we expect to get the existing OUT_OF_SERVICE lease when we re-register with UP.
        // First, we are "out of service".
        InstanceInfo myInstance = createLocalOutOfServiceInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Then, we re-register with "UP".
        InstanceInfo sameInstance = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registry.register(sameInstance, 10000000, false);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Let's try again. We shouldn't see a difference.
        sameInstance = createLocalInstance(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registry.register(sameInstance, 10000000, false);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.OUT_OF_SERVICE);
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

    /**
     * Verify the following behaviors:
     * <ul>
     * <li>1. Registration of new instances.</li>
     * <li>2. Lease expiration.</li>
     * <li>3. NumOfRenewsPerMinThreshold will be updated. Since this threshold will be updated according to
     * {@link EurekaServerConfig#getRenewalThresholdUpdateIntervalMs()}, and discovery client will try to get
     * applications count from peer or remote Eureka servers, the count number will be used to update threshold.</li>
     * </ul>
     * Below shows the time line of a bunch of events in 120 seconds. Here the following setting are configured during registry startup:
     * eureka.renewalThresholdUpdateIntervalMs=5000,
     * eureka.evictionIntervalTimerInMs=10000
     * <pre>
     * TimeInSecs 0          15         30    40   45         60        75   80      90         105       120
     *            |----------|----------|------|----|----------|---------|----|-------|----------|---------|
     * Events    (1)        (2)               (3)  (4)        (5)       (6)  (7)                (8)       (9)
     * </pre>
     * <ul>
     * <li>(1). Remote server started on random port, local registry started as well.
     * 50 instances will be registered to local registry with application name of {@link #LOCAL_REGION_APP_NAME}
     * and lease duration set to 30 seconds.
     * At this time isLeaseExpirationEnabled=false, getNumOfRenewsPerMinThreshold=
     * (50*2 + 2(which comes from remote registry))*85%=86</li>
     * <li>(2). 45 out of the 50 instances send heartbeats to local registry.</li>
     * <li>(3). Check registry status, isLeaseExpirationEnabled=false, getNumOfRenewsInLastMin=0,
     * getNumOfRenewsPerMinThreshold=86, registeredInstancesNumberOfMYLOCALAPP=50</li>
     * <li>(4). 45 out of the 50 instances send heartbeats to local registry.</li>
     * <li>(5). Accumulate one minutes data, and from now on, isLeaseExpirationEnabled=true,
     * getNumOfRenewsInLastMin=90. Because lease expiration is enabled, and lease for 5 instance are expired,
     * so evict 5 instances.</li>
     * <li>(6). 45 out of the 50 instances send heartbeats to local registry.</li>
     * <li>(7). Check registry status, isLeaseExpirationEnabled=true, getNumOfRenewsInLastMin=90,
     * getNumOfRenewsPerMinThreshold=86, registeredInstancesNumberOfMYLOCALAPP=45</li>
     * Remote region add another 150 instances to application of {@link #LOCAL_REGION_APP_NAME}.
     * This will make {@link PeerAwareInstanceRegistryImpl#updateRenewalThreshold()} to refresh
     * {@link AbstractInstanceRegistry#numberOfRenewsPerMinThreshold}.</li>
     * <li>(8). 45 out of the 50 instances send heartbeats to local registry.</li>
     * <li>(9). Check registry status, isLeaseExpirationEnabled=false, getNumOfRenewsInLastMin=90,
     * getNumOfRenewsPerMinThreshold=256, registeredInstancesNumberOfMYLOCALAPP=45</li>
     * </ul>
     */
    @Test
    @Ignore
    public void testLeaseExpirationAndUpdateRenewalThreshold() throws Exception {
        final int registeredInstanceCount = 50;
        final int leaseDurationInSecs = 30;

        AsyncSequentialExecutor executor = new AsyncSequentialExecutor();

        SequentialEvents registerMyLocalAppAndInstancesEvents = SequentialEvents.of(
            buildEvent(0, new SingleEvent.Action() {
                @Override
                public void execute() {
                    for (int j = 0; j < registeredInstanceCount; j++) {
                        registerInstanceLocallyWithLeaseDurationInSecs(createLocalInstanceWithIdAndStatus(LOCAL_REGION_APP_NAME,
                            LOCAL_REGION_INSTANCE_1_HOSTNAME + j, InstanceStatus.UP), leaseDurationInSecs);
                    }
                }
            })
        );

        SequentialEvents showRegistryStatusEvents = SequentialEvents.of(
            buildEvents(5, 24, new SingleEvent.Action() {
                @Override
                public void execute() {
                    System.out.println(String.format("isLeaseExpirationEnabled=%s, getNumOfRenewsInLastMin=%d, "
                            + "getNumOfRenewsPerMinThreshold=%s, instanceNumberOfMYLOCALAPP=%d", registry.isLeaseExpirationEnabled(),
                        registry.getNumOfRenewsInLastMin(), registry.getNumOfRenewsPerMinThreshold(),
                        registry.getApplication(LOCAL_REGION_APP_NAME).getInstances().size()));
                }
            })
        );

        SequentialEvents renewEvents = SequentialEvents.of(
            buildEvent(15, renewPartOfTheWholeInstancesAction()),
            buildEvent(30, renewPartOfTheWholeInstancesAction()),
            buildEvent(30, renewPartOfTheWholeInstancesAction()),
            buildEvent(30, renewPartOfTheWholeInstancesAction())
        );

        SequentialEvents remoteRegionAddMoreInstancesEvents = SequentialEvents.of(
            buildEvent(90, new SingleEvent.Action() {
                @Override
                public void execute() {
                    for (int i = 0; i < 150; i++) {
                        InstanceInfo newlyCreatedInstance = createLocalInstanceWithIdAndStatus(REMOTE_REGION_APP_NAME,
                            LOCAL_REGION_INSTANCE_1_HOSTNAME + i, InstanceStatus.UP);
                        remoteRegionApps.get(REMOTE_REGION_APP_NAME).addInstance(newlyCreatedInstance);
                        remoteRegionAppsDelta.get(REMOTE_REGION_APP_NAME).addInstance(newlyCreatedInstance);
                    }
                }
            })
        );

        SequentialEvents checkEvents = SequentialEvents.of(
            buildEvent(40, new SingleEvent.Action() {
                @Override
                public void execute() {
                    System.out.println("checking on 40s");
                    Preconditions.checkState(Boolean.FALSE.equals(registry.isLeaseExpirationEnabled()), "Lease expiration should be disabled");
                    Preconditions.checkState(registry.getNumOfRenewsInLastMin() == 0, "Renewals in last min should be 0");
                    Preconditions.checkState(registry.getApplication(LOCAL_REGION_APP_NAME).getInstances().size() == 50,
                        "There should be 50 instances in application - MYLOCAPP");
                }
            }),
            buildEvent(40, new SingleEvent.Action() {
                @Override
                public void execute() {
                    System.out.println("checking on 80s");
                    Preconditions.checkState(Boolean.TRUE.equals(registry.isLeaseExpirationEnabled()), "Lease expiration should be enabled");
                    Preconditions.checkState(registry.getNumOfRenewsInLastMin() == 90, "Renewals in last min should be 90");
                    Preconditions.checkState(registry.getApplication(LOCAL_REGION_APP_NAME).getInstances().size() == 45,
                        "There should be 45 instances in application - MYLOCAPP");
                }
            }),
            buildEvent(40, new SingleEvent.Action() {
                @Override
                public void execute() {
                    System.out.println("checking on 120s");
                    System.out.println("getNumOfRenewsPerMinThreshold=" + registry.getNumOfRenewsPerMinThreshold());
                    Preconditions.checkState(registry.getNumOfRenewsPerMinThreshold() == 256, "NumOfRenewsPerMinThreshold should be updated to 256");
                    Preconditions.checkState(registry.getApplication(LOCAL_REGION_APP_NAME).getInstances().size() == 45,
                        "There should be 45 instances in application - MYLOCAPP");
                }
            })
        );

        AsyncResult<AsyncSequentialExecutor.ResultStatus> registerMyLocalAppAndInstancesResult = executor.run(registerMyLocalAppAndInstancesEvents);
        AsyncResult<AsyncSequentialExecutor.ResultStatus> showRegistryStatusEventResult = executor.run(showRegistryStatusEvents);
        AsyncResult<AsyncSequentialExecutor.ResultStatus> renewResult = executor.run(renewEvents);
        AsyncResult<AsyncSequentialExecutor.ResultStatus> remoteRegionAddMoreInstancesResult = executor.run(remoteRegionAddMoreInstancesEvents);
        AsyncResult<AsyncSequentialExecutor.ResultStatus> checkResult = executor.run(checkEvents);

        Assert.assertEquals("Register application and instances failed", AsyncSequentialExecutor.ResultStatus.DONE, registerMyLocalAppAndInstancesResult.getResult());
        Assert.assertEquals("Show registry status failed", AsyncSequentialExecutor.ResultStatus.DONE, showRegistryStatusEventResult.getResult());
        Assert.assertEquals("Renew lease did not succeed", AsyncSequentialExecutor.ResultStatus.DONE, renewResult.getResult());
        Assert.assertEquals("More instances are registered to remote region did not succeed", AsyncSequentialExecutor.ResultStatus.DONE, remoteRegionAddMoreInstancesResult.getResult());
        Assert.assertEquals("Check failed", AsyncSequentialExecutor.ResultStatus.DONE, checkResult.getResult());
    }
}
