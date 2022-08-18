package com.netflix.eureka;

import com.netflix.eureka.mock.MockRemoteEurekaServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.doReturn;

/**
 * @author Nitesh Kant
 */
public class RemoteRegionSoftDependencyTest extends AbstractTester {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(10).when(serverConfig).getWaitTimeInMsWhenSyncEmpty();
        doReturn(1).when(serverConfig).getRegistrySyncRetries();
        doReturn(1l).when(serverConfig).getRegistrySyncRetryWaitMs();
        registry.syncUp();
    }

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
