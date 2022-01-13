package com.netflix.discovery;

import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for DiscoveryClient stats reported when initial registry fetch fails.
 */
public class DiscoveryClientStatsInitFailedTest extends BaseDiscoveryClientTester {

    @Before
    public void setUp() throws Exception {
        setupProperties();
        populateRemoteRegistryAtStartup();
        setupDiscoveryClient();
    }

    @After
    public void tearDown() throws Exception {
        shutdownDiscoveryClient();
        DiscoveryClientResource.clearDiscoveryClientConfig();
    }

    @Test
    public void testEmptyInitLocalRegistrySize() throws Exception {
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assert.assertEquals(0, clientImpl.getStats().initLocalRegistrySize());
    }

    @Test
    public void testInitFailed() throws Exception {
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assert.assertFalse(clientImpl.getStats().initSucceeded());
    }

}
