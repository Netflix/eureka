package com.netflix.eureka2.integration.startup;

import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaBridgeServer;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class BridgeServerStartupAndShutdownIntegrationTest extends
        AbstractStartupAndShutdownIntegrationTest<BridgeServerConfig, EurekaBridgeServer> {

    public static final String SERVER_NAME = "bridge-server-startupAndShutdown";

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, new EurekaBridgeServer(SERVER_NAME));
    }
}