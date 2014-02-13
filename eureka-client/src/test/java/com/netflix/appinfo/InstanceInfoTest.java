package com.netflix.appinfo;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by jzarfoss on 2/12/14.
 */
public class InstanceInfoTest {

    // contrived test to check copy constructor and verify behavior of builder for InstanceInfo

    @Test
    public void testCopyConstructor(){

        DataCenterInfo myDCI = new DataCenterInfo(){

            public DataCenterInfo.Name getName(){return DataCenterInfo.Name.MyOwn;}

        };


        InstanceInfo smallII1 = InstanceInfo.Builder.newBuilder().setAppName("test").setDataCenterInfo(myDCI).build();
        InstanceInfo smallII2 = new InstanceInfo(smallII1);

        Assert.assertFalse(smallII1 == smallII2);
        Assert.assertEquals(smallII1, smallII2);



        InstanceInfo fullII1 = InstanceInfo.Builder.newBuilder().setMetadata(null)
                .setOverriddenStatus(InstanceInfo.InstanceStatus.UNKNOWN)
                .setHostName("localhost")
                .setSecureVIPAddress("testSecureVIP:22")
                .setStatus(InstanceInfo.InstanceStatus.UNKNOWN)
                .setStatusPageUrl("relative", "explicit/relative")
                .setVIPAddress("testVIP:21")
                .setAppName("test").setASGName("testASG").setDataCenterInfo(myDCI)
                .setHealthCheckUrls("relative", "explicit/relative", "secureExplicit/relative")
                .setHomePageUrl("relativeHP", "explicitHP/relativeHP")
                .setIPAddr("127.0.0.1")
                .setPort(21).setSecurePort(22).build();

        InstanceInfo fullII2 = new InstanceInfo(fullII1);

        Assert.assertFalse(fullII1 == fullII2);
        Assert.assertEquals(fullII1, fullII2);
    }




}
