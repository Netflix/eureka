package com.netflix.discovery.util;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author David Liu
 */
public class EurekaUtilsTest {
    @Test
    public void testIsInEc2() {
        InstanceInfo instanceInfo1 = new InstanceInfo.Builder(InstanceInfoGenerator.takeOne())
                .setDataCenterInfo(new DataCenterInfo() {
                    @Override
                    public Name getName() {
                        return Name.MyOwn;
                    }
                })
                .build();

        Assertions.assertFalse(EurekaUtils.isInEc2(instanceInfo1));

        InstanceInfo instanceInfo2 = InstanceInfoGenerator.takeOne();
        Assertions.assertTrue(EurekaUtils.isInEc2(instanceInfo2));
    }

    @Test
    public void testIsInVpc() {
        InstanceInfo instanceInfo1 = new InstanceInfo.Builder(InstanceInfoGenerator.takeOne())
                .setDataCenterInfo(new DataCenterInfo() {
                    @Override
                    public Name getName() {
                        return Name.MyOwn;
                    }
                })
                .build();

        Assertions.assertFalse(EurekaUtils.isInVpc(instanceInfo1));

        InstanceInfo instanceInfo2 = InstanceInfoGenerator.takeOne();
        Assertions.assertFalse(EurekaUtils.isInVpc(instanceInfo2));

        InstanceInfo instanceInfo3 = InstanceInfoGenerator.takeOne();
        ((AmazonInfo) instanceInfo3.getDataCenterInfo()).getMetadata()
                .put(AmazonInfo.MetaDataKey.vpcId.getName(), "vpc-123456");

        Assertions.assertTrue(EurekaUtils.isInVpc(instanceInfo3));
    }
}
