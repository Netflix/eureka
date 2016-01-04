package com.netflix.eureka2.testkit.junit.matchers;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.EurekaMatchers;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class InstanceInfoMatcherTest {

    private static final InstanceInfo INFO = SampleInstanceInfo.DiscoveryServer.build();

    @Test(timeout = 60000)
    public void testMatchesSameEntity() throws Exception {
        InstanceInfo infoWithOtherVersion = InstanceModel.getDefaultModel().newInstanceInfo().withInstanceInfo(INFO).build();
        boolean result = EurekaMatchers.sameInstanceInfoAs(INFO).matches(infoWithOtherVersion);
        assertThat(result, is(true));
    }
}