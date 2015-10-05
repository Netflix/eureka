package com.netflix.discovery;

import com.netflix.discovery.junit.resource.DiscoveryClientResource;
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
        DiscoveryClientResource.clearDiscoveryClientConfig();
    }
}
