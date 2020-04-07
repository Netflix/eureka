package com.netflix.discovery;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for DiscoveryClient stats reported when initial registry fetch succeeds.
 */
public class DiscoveryClientStatsTest extends AbstractDiscoveryClientTester {

    @Test
    public void testNonEmptyInitLocalRegistrySize() throws Exception {
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assert.assertEquals(createLocalApps().size(), clientImpl.getStats().initLocalRegistrySize());
    }

    @Test
    public void testInitSucceeded() throws Exception {
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assert.assertTrue(clientImpl.getStats().initSucceeded());
    }

}
