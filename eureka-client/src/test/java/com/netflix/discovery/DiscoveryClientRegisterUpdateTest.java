package com.netflix.discovery;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

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

        applicationInfoManager = new TestApplicationInfoManager();
        applicationInfoManager.initComponent(new MyDataCenterInstanceConfig());
        client = new DiscoveryClient(applicationInfoManager, new DefaultEurekaClientConfig());
    }

    @After
    public void tearDown() throws Exception {
        client.shutdown();
        mockLocalEurekaServer.stop();
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void registerUpdateLifecycleTest() throws Exception {
        Thread.sleep(1200);  // give some execution time (the allowed on-demand interval is 60/min)
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        Thread.sleep(1200);  // give some execution time
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UNKNOWN);
        Thread.sleep(1200);  // give some execution time
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);

        Thread.sleep(2400);

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
        Thread.sleep(400);
        // this call will be rate limited, but will be transmitted by the automatic update after 10s
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        Thread.sleep(2400);

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
        TestApplicationInfoManager() {
            super(null, null);
        }

        public Map<String, StatusChangeListener> getStatusChangeListeners() {
            return this.listeners;
        }
    }
}
