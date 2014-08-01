package com.netflix.eureka;

import com.netflix.eureka.mock.MockRemoteEurekaServer;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Nitesh Kant
 */
public class RemoteRegionSoftDependencyTest extends AbstractTester {

    @Test
    public void testSoftDepRemoteDown() throws Exception {
        Assert.assertTrue("Registry access disallowed when remote region is down.", registry.shouldAllowAccess(false));
        Assert.assertFalse("Registry access allowed when remote region is down.", registry.shouldAllowAccess(true));
    }

    @Override
    protected MockRemoteEurekaServer newMockRemoteServer() {
        MockRemoteEurekaServer server = super.newMockRemoteServer();
        server.simulateNotReady(true);
        return server;
    }
}
