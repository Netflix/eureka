package com.netflix.eureka;

import com.netflix.eureka.mock.MockRemoteEurekaServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doReturn;

/**
 * @author Nitesh Kant
 */
public class RemoteRegionSoftDependencyTest extends AbstractTester {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        doReturn(10).when(serverConfig).getWaitTimeInMsWhenSyncEmpty();
        doReturn(1).when(serverConfig).getRegistrySyncRetries();
        doReturn(1l).when(serverConfig).getRegistrySyncRetryWaitMs();
        registry.syncUp();
    }

    @Test
    public void testSoftDepRemoteDown() throws Exception {
        Assertions.assertTrue(registry.shouldAllowAccess(false), "Registry access disallowed when remote region is down.");
        Assertions.assertFalse(registry.shouldAllowAccess(true), "Registry access allowed when remote region is down.");
    }

    @Override
    protected MockRemoteEurekaServer newMockRemoteServer() {
        MockRemoteEurekaServer server = super.newMockRemoteServer();
        server.simulateNotReady(true);
        return server;
    }
}
