package com.netflix.discovery;

import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for DiscoveryClient stats reported when initial registry fetch fails.
 */
public class DiscoveryClientStatsInitFailedTest extends BaseDiscoveryClientTester {

    @BeforeEach
    public void setUp() throws Exception {
        setupProperties();
        populateRemoteRegistryAtStartup();
        setupDiscoveryClient();
    }

    @AfterEach
    public void tearDown() throws Exception {
        shutdownDiscoveryClient();
        DiscoveryClientResource.clearDiscoveryClientConfig();
    }

    @Test
    public void testEmptyInitLocalRegistrySize() throws Exception {
        Assertions.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assertions.assertEquals(0, clientImpl.getStats().initLocalRegistrySize());
    }

    @Test
    public void testInitFailed() throws Exception {
        Assertions.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assertions.assertFalse(clientImpl.getStats().initSucceeded());
    }

}
