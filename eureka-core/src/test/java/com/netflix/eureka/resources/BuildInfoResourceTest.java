package com.netflix.eureka.resources;

import com.netflix.eureka.AbstractTester;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.matchesPattern;

/**
 * Test class for BuildInfoResource.
 *
 * @author Test Generator
 */
public class BuildInfoResourceTest extends AbstractTester {

    private BuildInfoResource buildInfoResource;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        buildInfoResource = new BuildInfoResource(serverContext);
    }

    @Test
    public void testGetBuildInfo_containsAllRequiredFields() {
        Map<String, Object> buildInfo = buildInfoResource.getBuildInfo();

        assertThat("buildInfo should not be null", buildInfo, is(notNullValue()));
        assertThat("buildVersion field should be present", buildInfo.containsKey("buildVersion"), is(true));
        assertThat("javaVersion field should be present", buildInfo.containsKey("javaVersion"), is(true));
        assertThat("uptimeSeconds field should be present", buildInfo.containsKey("uptimeSeconds"), is(true));
        assertThat("serverTime field should be present", buildInfo.containsKey("serverTime"), is(true));
    }

    @Test
    public void testGetBuildInfo_buildVersionIsNonNull() {
        Map<String, Object> buildInfo = buildInfoResource.getBuildInfo();

        Object buildVersion = buildInfo.get("buildVersion");
        assertThat("buildVersion should not be null", buildVersion, is(notNullValue()));
        assertThat("buildVersion should be a String", buildVersion instanceof String, is(true));
        assertThat("buildVersion should not be empty", ((String) buildVersion).isEmpty(), is(false));
    }

    @Test
    public void testGetBuildInfo_javaVersionIsValid() {
        Map<String, Object> buildInfo = buildInfoResource.getBuildInfo();

        Object javaVersion = buildInfo.get("javaVersion");
        assertThat("javaVersion should not be null", javaVersion, is(notNullValue()));
        assertThat("javaVersion should be a String", javaVersion instanceof String, is(true));
        assertThat("javaVersion should not be empty", ((String) javaVersion).isEmpty(), is(false));
    }

    @Test
    public void testGetBuildInfo_uptimeSecondsIsNonNegative() {
        Map<String, Object> buildInfo = buildInfoResource.getBuildInfo();

        Object uptimeSeconds = buildInfo.get("uptimeSeconds");
        assertThat("uptimeSeconds should not be null", uptimeSeconds, is(notNullValue()));
        assertThat("uptimeSeconds should be a Number", uptimeSeconds instanceof Number, is(true));
        
        long uptime = ((Number) uptimeSeconds).longValue();
        assertThat("uptimeSeconds should be non-negative", uptime, is(greaterThanOrEqualTo(0L)));
    }

    @Test
    public void testGetBuildInfo_serverTimeIsISO8601Format() {
        Map<String, Object> buildInfo = buildInfoResource.getBuildInfo();

        Object serverTime = buildInfo.get("serverTime");
        assertThat("serverTime should not be null", serverTime, is(notNullValue()));
        assertThat("serverTime should be a String", serverTime instanceof String, is(true));
        
        String timeString = (String) serverTime;
        // ISO-8601 format should match pattern like: 2026-06-03T14:45:53Z or 2026-06-03T14:45:53.123Z
        assertThat("serverTime should be in ISO-8601 format", 
            timeString, 
            matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z?.*"));
    }

    @Test
    public void testGetBuildInfo_uptimeIncreasesBetweenCalls() throws InterruptedException {
        Map<String, Object> buildInfo1 = buildInfoResource.getBuildInfo();
        long uptime1 = ((Number) buildInfo1.get("uptimeSeconds")).longValue();

        // Sleep for a short time to ensure uptime increases
        Thread.sleep(100);

        Map<String, Object> buildInfo2 = buildInfoResource.getBuildInfo();
        long uptime2 = ((Number) buildInfo2.get("uptimeSeconds")).longValue();

        // Uptime should either stay the same or increase (it increases but might be same second)
        assertThat("uptimeSeconds should not decrease between calls", uptime2, is(greaterThanOrEqualTo(uptime1)));
    }

    @Test
    public void testGetBuildInfo_canBeCalledMultipleTimes() {
        // Call the method multiple times to ensure it's idempotent and doesn't fail
        Map<String, Object> buildInfo1 = buildInfoResource.getBuildInfo();
        Map<String, Object> buildInfo2 = buildInfoResource.getBuildInfo();
        Map<String, Object> buildInfo3 = buildInfoResource.getBuildInfo();

        assertThat(buildInfo1, is(notNullValue()));
        assertThat(buildInfo2, is(notNullValue()));
        assertThat(buildInfo3, is(notNullValue()));
    }

    @Test
    public void testConstructorWithoutServerContext() {
        // Test the no-arg constructor that uses EurekaServerContextHolder
        BuildInfoResource resource = new BuildInfoResource();
        Map<String, Object> buildInfo = resource.getBuildInfo();

        assertThat("buildInfo should not be null when using default constructor", buildInfo, is(notNullValue()));
        assertThat("buildInfo should contain buildVersion", buildInfo.containsKey("buildVersion"), is(true));
    }
}
