package com.netflix.discovery;

import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * @author Nitesh Kant
 */
public abstract class AbstractDiscoveryClientTester extends BaseDiscoveryClientTester {

    @BeforeEach
    public void setUp() throws Exception {

        setupProperties();

        populateLocalRegistryAtStartup();
        populateRemoteRegistryAtStartup();

        setupDiscoveryClient();
    }

    @AfterEach
    public void tearDown() throws Exception {
        shutdownDiscoveryClient();
        DiscoveryClientResource.clearDiscoveryClientConfig();
    }
}
