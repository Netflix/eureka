package com.netflix.discovery;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Applications;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * @author Nitesh Kant
 */
public class DiscoveryClientRegistryTest extends AbstractDiscoveryClientTester {

    @Test
    public void testGetByVipInLocalRegion() throws Exception {
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false);
        Assert.assertEquals("Unexpected number of instances found for local region.", 1, instancesByVipAddress.size());
        InstanceInfo instance = instancesByVipAddress.iterator().next();
        Assert.assertEquals("Local instance not returned for local region vip address",
                            LOCAL_REGION_INSTANCE_1_HOSTNAME, instance.getHostName());
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
        List<InstanceInfo> instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false, REMOTE_REGION);
        Assert.assertEquals("Unexpected number of instances found for remote region.", 1, instancesByVipAddress.size());
        InstanceInfo instance = instancesByVipAddress.iterator().next();
        Assert.assertEquals("Remote instance not returned for remote region vip address", REMOTE_REGION_INSTANCE_1_HOSTNAME, instance.getHostName());
    }

    @Test
    public void testDelta() throws Exception {
        mockLocalEurekaServer.waitForDeltaToBeRetrieved(CLIENT_REFRESH_RATE);

        checkInstancesFromARegion("local", LOCAL_REGION_INSTANCE_1_HOSTNAME,
                                  LOCAL_REGION_INSTANCE_2_HOSTNAME);
        checkInstancesFromARegion(REMOTE_REGION, REMOTE_REGION_INSTANCE_1_HOSTNAME,
                                  REMOTE_REGION_INSTANCE_2_HOSTNAME);
    }

    @Test
    public void testAppsHashCodeAfterRefresh() throws Exception {
        Assert.assertEquals("UP_2_", client.getApplications().getAppsHashCode());

        addLocalAppDelta();
        mockLocalEurekaServer.waitForDeltaToBeRetrieved(CLIENT_REFRESH_RATE);

        Assert.assertEquals("UP_3_", client.getApplications().getAppsHashCode());
    }

    private void checkInstancesFromARegion(String region, String instance1Hostname, String instance2Hostname) {
        List<InstanceInfo> instancesByVipAddress;
        if ("local".equals(region)) {
            instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false);
        } else {
            instancesByVipAddress = client.getInstancesByVipAddress(ALL_REGIONS_VIP_ADDR, false, region);
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
