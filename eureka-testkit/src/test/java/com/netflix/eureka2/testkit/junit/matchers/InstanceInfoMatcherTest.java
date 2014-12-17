package com.netflix.eureka2.testkit.junit.matchers;

import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.InstanceInfo.Builder;
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
    public void testMatchesSameEntityDifferentVersions() throws Exception {
        InstanceInfo infoWithOtherVersion = new Builder().withInstanceInfo(INFO).withVersion(INFO.getVersion() + 1).build();
        boolean result = EurekaMatchers.sameInstanceInfoAs(INFO).matches(infoWithOtherVersion);
        assertThat("Identical instanceInfo objects, diverging only by version number should pass this test", result, is(true));
    }

    @Test
    public void testMatchesIdenticalEntitiesOnly() throws Exception {
        InstanceInfo infoWithOtherVersion = new Builder().withInstanceInfo(INFO).withVersion(INFO.getVersion() + 1).build();
        boolean result = EurekaMatchers.identicalInstanceInfoAs(INFO).matches(infoWithOtherVersion);
        assertThat("Two instance info objects with different version numbers should fail this test", result, is(false));

        result = EurekaMatchers.identicalInstanceInfoAs(INFO).matches(INFO);
        assertThat("Identical instanceInfo objects should pass this test", result, is(true));
    }
}