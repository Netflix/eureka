package com.netflix.eureka.registry;

import com.google.common.base.Preconditions;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.eureka.AbstractTester;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.test.async.executor.AsyncResult;
import com.netflix.eureka.test.async.executor.AsyncSequentialExecutor;
import com.netflix.eureka.test.async.executor.SequentialEvents;
import com.netflix.eureka.test.async.executor.SingleEvent;
import org.junit.Assert;
import org.junit.Test;

/**
 * Time consuming test case for {@link InstanceRegistry}.
 *
 * @author neoremind
 */
public class TimeConsumingInstanceRegistryTest extends AbstractTester {

    /**
     * Verify the following behaviors, the test case will run for 2 minutes.
     * <ul>
     * <li>1. Registration of new instances.</li>
     * <li>2. Lease expiration.</li>
     * <li>3. NumOfRenewsPerMinThreshold will be updated. Since this threshold will be updated according to
     * {@link EurekaServerConfig#getRenewalThresholdUpdateIntervalMs()}, and discovery client will try to get
     * applications count from peer or remote Eureka servers, the count number will be used to update threshold.</li>
     * </ul>
     * </p>
     * Below shows the time line of a bunch of events in 120 seconds. Here the following setting are configured during registry startup:
     * <code>eureka.renewalThresholdUpdateIntervalMs=5000</code>,
     * <code>eureka.evictionIntervalTimerInMs=10000</code>,
     * <code>eureka.renewalPercentThreshold=0.85</code>
     * </p>
     * <pre>
     * TimeInSecs 0          15         30    40   45         60        75   80      90         105       120
     *            |----------|----------|------|----|----------|---------|----|-------|----------|---------|
     * Events    (1)        (2)               (3)  (4)        (5)       (6)  (7)                (8)       (9)
     * </pre>
     * </p>
     * <ul>
     * <li>(1). Remote server started on random port, local registry started as well.
     * 50 instances will be registered to local registry with application name of {@link #LOCAL_REGION_APP_NAME}
     * and lease duration set to 30 seconds.
     * At this time isLeaseExpirationEnabled=false, getNumOfRenewsPerMinThreshold=
     * (50*2 + 1(initial value))*85%=86</li>
     * <li>(2). 45 out of the 50 instances send heartbeats to local registry.</li>
     * <li>(3). Check registry status, isLeaseExpirationEnabled=false, getNumOfRenewsInLastMin=0,
     * getNumOfRenewsPerMinThreshold=86, registeredInstancesNumberOfMYLOCALAPP=50</li>
     * <li>(4). 45 out of the 50 instances send heartbeats to local registry.</li>
     * <li>(5). Accumulate one minutes data, and from now on, isLeaseExpirationEnabled=true,
     * getNumOfRenewsInLastMin=90. Because lease expiration is enabled, and lease for 5 instance are expired,
     * so when eviction thread is working, the 5 instances will be marked as deleted.</li>
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
     * </p>
     * Note that there is a thread retrieving and printing out registry status for debugging purpose.
     */
    @Test
    public void testLeaseExpirationAndUpdateRenewalThreshold() throws InterruptedException {
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
