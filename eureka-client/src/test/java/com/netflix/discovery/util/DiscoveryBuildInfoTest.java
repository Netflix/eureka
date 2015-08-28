package com.netflix.discovery.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class DiscoveryBuildInfoTest {

    @Test
    public void testRequestedManifestIsLocatedAndLoaded() throws Exception {
        DiscoveryBuildInfo buildInfo = new DiscoveryBuildInfo(ObjectMapper.class);
        assertThat(buildInfo.getBuildVersion().contains("version_unknown"), is(false));
    }
}