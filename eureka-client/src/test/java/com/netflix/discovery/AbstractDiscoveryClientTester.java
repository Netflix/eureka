package com.netflix.discovery;

import org.junit.After;
import org.junit.Before;

/**
 * @author Nitesh Kant
 */
public abstract class AbstractDiscoveryClientTester extends BaseDiscoveryClientTester {

    @Before
    public void setUp() throws Exception {

        setupProperties();

        populateLocalRegistryAtStartup();
        populateRemoteRegistryAtStartup();

        setupDiscoveryClient();
    }

    @After
    public void tearDown() throws Exception {
        shutdownDiscoveryClient();
        DiscoveryClientRule.clearDiscoveryClientConfig();
    }
}
