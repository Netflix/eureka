package com.netflix.discovery;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Nitesh Kant
 */
public class DiscoveryClientRegistryTest extends AbstractDiscoveryClientTester {

    @Test
    public void testGetByVipInLocalRegion() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP1_ADDR, false);
        Assert.assertEquals("Unexpected number of instances found for local region.", 1, instancesByVipAddress.size());
        InstanceInfo instance = instancesByVipAddress.iterator().next();
        Assert.assertEquals("Local instance not returned for local region vip address",
                LOCAL_REGION_APP1_INSTANCE1_HOSTNAME, instance.getHostName());
    }

    @Test
    public void testGetAllKnownRegions() throws Exception {
        Set<String> allKnownRegions = client.getAllKnownRegions();
        Assert.assertEquals("Unexpected number of known regions." + allKnownRegions, 2, allKnownRegions.size());
        Assert.assertTrue("Remote region not found in set of known regions." + allKnownRegions, allKnownRegions.contains(REMOTE_REGION));
    }

    @Test
    public void testAllAppsForRegions() throws Exception {
        Applications appsForRemoteRegion = client.getApplicationsForARegion(REMOTE_REGION);
        Assert.assertTrue("No apps for remote region found.", null != appsForRemoteRegion && !appsForRemoteRegion.getRegisteredApplications().isEmpty());
        Applications appsForLocalRegion = client.getApplicationsForARegion("us-east-1");
        Assert.assertTrue("No apps for local region found.", null != appsForLocalRegion && !appsForLocalRegion.getRegisteredApplications().isEmpty());
    }

    @Test
    public void testCacheRefreshSingleAppForLocalRegion() throws Exception {
        final String propertyName = "eureka.registryRefreshSingleVipAddress";
        try {
            shutdownDiscoveryClient();  // shutdown and restart to pick up new configs
            ConfigurationManager.getConfigInstance().setProperty(propertyName, ALL_REGIONS_VIP1_ADDR);
            setupDiscoveryClient();

            List<Application> registeredApps = client.getApplications().getRegisteredApplications();
            Assert.assertEquals(1, registeredApps.size());

            Application app = registeredApps.get(0);
            Assert.assertEquals(LOCAL_REGION_APP1_NAME, app.getName());

            List<InstanceInfo> instances = app.getInstances();
            Assert.assertEquals(1, instances.size());
        } finally {
            ConfigurationManager.getConfigInstance().clearProperty(propertyName);
        }
    }

    @Test
    public void testEurekaClientPeriodicHeartbeat() throws Exception {
        final String shouldFetchRegistryPropName = "eureka.shouldFetchRegistry";
        final String fetchRegistryOrigValue =
                (String) ConfigurationManager.getConfigInstance().getProperty(shouldFetchRegistryPropName);

        final String enableHeartbeartPropName = "eureka.registration.enabled";
        final String heartbeatOrigValue =
                (String) ConfigurationManager.getConfigInstance().getProperty(enableHeartbeartPropName);

        try {
            shutdownDiscoveryClient();  // shutdown and restart to pick up new configs
            ConfigurationManager.getConfigInstance().setProperty(shouldFetchRegistryPropName, "false");
            ConfigurationManager.getConfigInstance().setProperty(enableHeartbeartPropName, "true");
            setupDiscoveryClient(3);

            Assert.assertEquals(0, mockLocalEurekaServer.heartbeatCount.get());

            // let the test run for just over 6 seconds to get two heartbeats
            Thread.sleep(7*1000);

            Assert.assertEquals(2, mockLocalEurekaServer.heartbeatCount.get());

        } finally {
            ConfigurationManager.getConfigInstance().setProperty(enableHeartbeartPropName, heartbeatOrigValue);
            ConfigurationManager.getConfigInstance().setProperty(shouldFetchRegistryPropName, fetchRegistryOrigValue);
        }
    }

    @Test
    public void testEurekaClientPeriodicCacheRefresh() throws Exception {
        final String shouldFetchRegistryPropName = "eureka.shouldFetchRegistry";
        final String fetchRegistryOrigValue =
                (String) ConfigurationManager.getConfigInstance().getProperty(shouldFetchRegistryPropName);

        final String fetchRegistryIntervalPropName = "eureka.client.refresh.interval";
        final int fetchRegistryIntervalOrigValue =
                (Integer) ConfigurationManager.getConfigInstance().getProperty(fetchRegistryIntervalPropName);

        try {
            shutdownDiscoveryClient();  // shutdown and restart to pick up new configs
            ConfigurationManager.getConfigInstance().setProperty(shouldFetchRegistryPropName, "true");
            ConfigurationManager.getConfigInstance().setProperty(fetchRegistryIntervalPropName, 3);
            setupDiscoveryClient();

            // initial setup calls getFull, and we setup the client twice
            Assert.assertEquals(2, mockLocalEurekaServer.getFullRegistryCount.get());
            Assert.assertEquals(0, mockLocalEurekaServer.getDeltaCount.get());

            // let the test run for just over 6 seconds to get two registry (delta) fetches
            Thread.sleep(7*1000);

            Assert.assertEquals(2, mockLocalEurekaServer.getFullRegistryCount.get());  // assert no more calls
            Assert.assertEquals(2, mockLocalEurekaServer.getDeltaCount.get());

        } finally {
            ConfigurationManager.getConfigInstance().setProperty(fetchRegistryIntervalPropName, fetchRegistryIntervalOrigValue);
            ConfigurationManager.getConfigInstance().setProperty(shouldFetchRegistryPropName, fetchRegistryOrigValue);
        }
    }

    @Test
    public void testGetInvalidVIP() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress("XYZ", false);
        Assert.assertEquals("Unexpected number of instances found for local region.", 0, instancesByVipAddress.size());
    }

    @Test
    public void testGetInvalidVIPForRemoteRegion() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress("XYZ", false,
                                                                                   REMOTE_REGION);
        Assert.assertEquals("Unexpected number of instances found for local region.", 0,
                            instancesByVipAddress.size());
    }

    @Test
    public void testGetByVipInRemoteRegion() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP1_ADDR, false, REMOTE_REGION);
        Assert.assertEquals("Unexpected number of instances found for remote region.", 1, instancesByVipAddress.size());
        InstanceInfo instance = instancesByVipAddress.iterator().next();
        Assert.assertEquals("Remote instance not returned for remote region vip address", REMOTE_REGION_APP1_INSTANCE1_HOSTNAME, instance.getHostName());
    }

    @Test
    public void testDelta() throws Exception {
        mockLocalEurekaServer.waitForDeltaToBeRetrieved(CLIENT_REFRESH_RATE);

        checkInstancesFromARegion("local", LOCAL_REGION_APP1_INSTANCE1_HOSTNAME,
                LOCAL_REGION_APP1_INSTANCE2_HOSTNAME);
        checkInstancesFromARegion(REMOTE_REGION, REMOTE_REGION_APP1_INSTANCE1_HOSTNAME,
                REMOTE_REGION_APP1_INSTANCE2_HOSTNAME);
    }

    @Test
    public void testAppsHashCodeAfterRefresh() throws Exception {
        Assert.assertEquals("UP_4_", client.getApplications().getAppsHashCode());

        addLocalAppDelta();
        mockLocalEurekaServer.waitForDeltaToBeRetrieved(CLIENT_REFRESH_RATE);

        Assert.assertEquals("UP_5_", client.getApplications().getAppsHashCode());
    }

    private void checkInstancesFromARegion(String region, String instance1Hostname, String instance2Hostname) {
        List<InstanceInfo> instancesByVipAddress;
        if ("local".equals(region)) {
            instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP1_ADDR, false);
        } else {
            instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP1_ADDR, false, region);
        }
        Assert.assertEquals("Unexpected number of instances found for " + region + " region.", 2,
                            instancesByVipAddress.size());
        InstanceInfo localInstance1 = null;
        InstanceInfo localInstance2 = null;
        for (InstanceInfo instance : instancesByVipAddress) {
            if (instance.getHostName().equals(instance1Hostname)) {
                localInstance1 = instance;
            } else if (instance.getHostName().equals(instance2Hostname)) {
                localInstance2 = instance;
            }
        }

        Assert.assertNotNull("Expected instance not returned for " + region + " region vip address", localInstance1);
        Assert.assertNotNull("Instance added as delta not returned for " + region + " region vip address", localInstance2);
    }

}
