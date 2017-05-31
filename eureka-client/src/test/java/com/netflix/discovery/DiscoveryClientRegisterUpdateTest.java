package com.netflix.discovery;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class DiscoveryClientRegisterUpdateTest {

    private TestApplicationInfoManager applicationInfoManager;
    private MockRemoteEurekaServer mockLocalEurekaServer;
    private EurekaClient client;

    @Before
    public void setUp() throws Exception {
        mockLocalEurekaServer = new MockRemoteEurekaServer();
        mockLocalEurekaServer.start();

        ConfigurationManager.getConfigInstance().setProperty("eureka.name", "EurekaTestApp-" + UUID.randomUUID());
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "true");
        ConfigurationManager.getConfigInstance().setProperty("eureka.appinfo.replicate.interval", 2);
        ConfigurationManager.getConfigInstance().setProperty("eureka.shouldFetchRegistry", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                "http://localhost:" + mockLocalEurekaServer.getPort() +
                        MockRemoteEurekaServer.EUREKA_API_BASE_PATH);

        InstanceInfo seed = InstanceInfoGenerator.takeOne();
        LeaseInfo leaseSeed = seed.getLeaseInfo();
        LeaseInfo leaseInfo = LeaseInfo.Builder.newBuilder()
                .setDurationInSecs(leaseSeed.getDurationInSecs())
                .setEvictionTimestamp(leaseSeed.getEvictionTimestamp())
                .setRegistrationTimestamp(leaseSeed.getRegistrationTimestamp())
                .setServiceUpTimestamp(leaseSeed.getServiceUpTimestamp())
                .setRenewalTimestamp(leaseSeed.getRenewalTimestamp())
                .setRenewalIntervalInSecs(2) // make this more frequent for testing
                .build();
        InstanceInfo instanceInfo = new InstanceInfo.Builder(seed)
                .setStatus(InstanceInfo.InstanceStatus.STARTING)
                .setLeaseInfo(leaseInfo)
                .build();
        applicationInfoManager = new TestApplicationInfoManager(instanceInfo);
        client = new DiscoveryClient(applicationInfoManager, new DefaultEurekaClientConfig());

        // force the initial registration to eagerly run
        InstanceInfoReplicator instanceInfoReplicator = ((DiscoveryClient) client).getInstanceInfoReplicator();
        instanceInfoReplicator.run();

        // give some execution time for the initial registration to process
        expectStatuses(1, 4000, TimeUnit.MILLISECONDS);
        mockLocalEurekaServer.registrationStatuses.clear();  // and then clear the validation list
        mockLocalEurekaServer.registerCount.set(0l);
    }

    @After
    public void tearDown() throws Exception {
        client.shutdown();
        mockLocalEurekaServer.stop();
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void registerUpdateLifecycleTest() throws Exception {
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        // give some execution time
        expectStatuses(1, 1200, TimeUnit.MILLISECONDS);
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UNKNOWN);
        // give some execution time
        expectStatuses(1, 1200, TimeUnit.MILLISECONDS);
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
        // give some execution time
        expectStatuses(1, 2400, TimeUnit.MILLISECONDS);

        Assert.assertEquals(Arrays.asList("UP", "UNKNOWN", "DOWN"), mockLocalEurekaServer.registrationStatuses);
        Assert.assertEquals(3, mockLocalEurekaServer.registerCount.get());
    }

    /**
     * This test is similar to the normal lifecycle test, but don't sleep between calls of setInstanceStatus
     */
    @Test
    public void registerUpdateQuickLifecycleTest() throws Exception {


        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UNKNOWN);
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
        expectStatuses(1, 400, TimeUnit.MILLISECONDS);
        // this call will be rate limited, but will be transmitted by the automatic update after 10s
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        expectStatuses(1, 2400, TimeUnit.MILLISECONDS);

        Assert.assertEquals(Arrays.asList("DOWN", "UP"), mockLocalEurekaServer.registrationStatuses);
        Assert.assertEquals(2, mockLocalEurekaServer.registerCount.get());
    }

    @Test
    public void registerUpdateShutdownTest() throws Exception {
        Assert.assertEquals(1, applicationInfoManager.getStatusChangeListeners().size());
        client.shutdown();
        Assert.assertEquals(0, applicationInfoManager.getStatusChangeListeners().size());
    }

    @Test
    public void testRegistrationDisabled() throws Exception {
        client.shutdown();  // shutdown the default @Before client first

        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        client = new DiscoveryClient(applicationInfoManager.getInfo(), new DefaultEurekaClientConfig());
        Assert.assertEquals(0, applicationInfoManager.getStatusChangeListeners().size());
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        Thread.sleep(400);
        client.shutdown();
        Assert.assertEquals(0, applicationInfoManager.getStatusChangeListeners().size());
    }

    public class TestApplicationInfoManager extends ApplicationInfoManager {
        TestApplicationInfoManager(InstanceInfo instanceInfo) {
            super(new MyDataCenterInstanceConfig(), instanceInfo, null);
        }

        public Map<String, StatusChangeListener> getStatusChangeListeners() {
            return this.listeners;
        }
    }

    private void expectStatuses(int n, long timeout, TimeUnit timeUnit) throws InterruptedException {
        for (int i = 0; i < n; i++) {
            String status = mockLocalEurekaServer.registrationStatusesQueue.poll(timeout, timeUnit);
            Assert.assertNotNull(status);
        }
    }
}
