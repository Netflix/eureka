package com.netflix.discovery;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for DiscoveryClient stats reported when initial registry fetch succeeds.
 */
public class DiscoveryClientStatsTest extends AbstractDiscoveryClientTester {

    @Test
    public void testNonEmptyInitLocalRegistrySize() throws Exception {
        Assertions.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assertions.assertEquals(createLocalApps().size(), clientImpl.getStats().initLocalRegistrySize());
    }

    @Test
    public void testInitSucceeded() throws Exception {
        Assertions.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assertions.assertTrue(clientImpl.getStats().initSucceeded());
    }

}
