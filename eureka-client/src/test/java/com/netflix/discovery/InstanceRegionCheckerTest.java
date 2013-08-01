package com.netflix.discovery;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import junit.framework.Assert;
import org.junit.Test;

/**
 * @author Nitesh Kant
 */
public class InstanceRegionCheckerTest {

    @Test
    public void testDefaults() throws Exception {
        InstanceRegionChecker checker =
                new InstanceRegionChecker("us-east-1", new DefaultEurekaClientConfig());
        AmazonInfo dcInfo = AmazonInfo.Builder.newBuilder().addMetadata(AmazonInfo.MetaDataKey.availabilityZone,
                                                                              "us-east-1c").build();
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder().setAppName("abc").setDataCenterInfo(dcInfo).build();
        String instanceRegion = checker.getInstanceRegion(instanceInfo);

        Assert.assertEquals("Invalid instance region.", "us-east-1", instanceRegion);
    }

    @Test
    public void testDefaultOverride() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("eureka.us-east-1.availabilityZones", "abc,def");
        InstanceRegionChecker checker =
                new InstanceRegionChecker("us-east-1", new DefaultEurekaClientConfig());
        AmazonInfo dcInfo = AmazonInfo.Builder.newBuilder().addMetadata(AmazonInfo.MetaDataKey.availabilityZone,
                                                                              "def").build();
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder().setAppName("abc").setDataCenterInfo(
                dcInfo).build();
        String instanceRegion = checker.getInstanceRegion(instanceInfo);

        Assert.assertEquals("Invalid instance region.", "us-east-1", instanceRegion);
    }
}
