package com.netflix.eureka.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.netflix.eureka.SampleInstanceInfo;
import org.junit.Test;

/**
 * @author David Liu
 */
public class DeltaTest {

    @Test
    public void testSettingFieldOnInstanceInfo() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        Delta delta = new Delta<String>(InstanceInfoField.APPLICATION, "customApp");
        delta.applyTo(instanceInfo);
        assertThat(instanceInfo.getApp(), equalTo("customApp"));
    }
}
