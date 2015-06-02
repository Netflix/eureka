package com.netflix.discovery;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

/**
 * @author David Liu
 */
public class DiscoveryClientRegisterUpdateTest {

    private ApplicationInfoManager applicationInfoManager;
    private MockRemoteEurekaServer mockLocalEurekaServer;

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

        applicationInfoManager = ApplicationInfoManager.getInstance();
        applicationInfoManager.initComponent(new MyDataCenterInstanceConfig());
        EurekaClient client = new DiscoveryClient(applicationInfoManager.getInfo(), new DefaultEurekaClientConfig());
    }

    @Test
    public void registerUpdateLifecycleTest() throws Exception {
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        Thread.sleep(400);  // give some execution time
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UNKNOWN);
        Thread.sleep(400);  // give some execution time
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
}
