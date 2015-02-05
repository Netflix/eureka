package com.netflix.eureka2.integration.startup;

import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaWriteServer;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ResolverType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig.WriteServerConfigBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * This test suite verifies that write server starts up successfully, given configuration
 * parameters either from file or command line explicitly.
 *
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class WriteServerStartupAndShutdownIntegrationTest
        extends AbstractStartupAndShutdownIntegrationTest<WriteServerConfig, EurekaWriteServer> {

    public static final String SERVER_NAME = "write-server-startupAndShutdown";

    @Test(timeout = 60000)
    public void testStartsWithCommandLineParameters() throws Exception {
        WriteServerConfig config = new WriteServerConfigBuilder()
                .withAppName(SERVER_NAME)
                .withResolverType(ResolverType.fixed)
                .withRegistrationPort(0)  // use ephemeral ports
                .withDiscoveryPort(0)
                .withReplicationPort(0)
                .withWebAdminPort(0)
                .withShutDownPort(0)
                .withServerList(writeServerList)
                .build();
        EurekaWriteServer server = new EurekaWriteServer(config);
        executeAndVerifyLifecycle(server, SERVER_NAME);
    }

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, new EurekaWriteServer(SERVER_NAME));
    }
}
