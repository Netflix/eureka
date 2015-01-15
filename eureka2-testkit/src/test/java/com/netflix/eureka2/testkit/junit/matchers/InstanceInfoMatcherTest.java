package com.netflix.eureka2.testkit.junit.matchers;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.EurekaMatchers;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class InstanceInfoMatcherTest {

    private static final InstanceInfo INFO = SampleInstanceInfo.DiscoveryServer.build();

    @Test
    public void testMatchesSameEntity() throws Exception {
        InstanceInfo infoWithOtherVersion = new Builder().withInstanceInfo(INFO).build();
        boolean result = EurekaMatchers.sameInstanceInfoAs(INFO).matches(infoWithOtherVersion);
        assertThat(result, is(true));
    }
}