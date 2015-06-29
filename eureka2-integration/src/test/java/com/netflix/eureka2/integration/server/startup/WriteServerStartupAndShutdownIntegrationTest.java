package com.netflix.eureka2.integration.server.startup;

import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaWriteServerRunner;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig.WriteServerConfigBuilder;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * This test suite verifies that write server starts up successfully, given configuration
 * parameters either from file or command line explicitly.
 *
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class WriteServerStartupAndShutdownIntegrationTest extends AbstractStartupAndShutdownIntegrationTest<EurekaWriteServerRunner> {

    public static final String SERVER_NAME = "write-server-startupAndShutdown";

    @Test(timeout = 60000)
    public void testStartsWithCommandLineParameters() throws Exception {
        WriteServerConfig config = new WriteServerConfigBuilder()
                .withAppName(SERVER_NAME)
                .withResolverType(ResolverType.Fixed)
                .withRegistrationPort(0)  // use ephemeral ports
                .withDiscoveryPort(0)
                .withReplicationPort(0)
                .withWebAdminPort(0)
                .withShutDownPort(0)
                .withServerList(writeServerList)
                .build();
        EurekaWriteServerRunner serverRunner = new EurekaWriteServerRunner(config);
        executeAndVerifyLifecycle(serverRunner, SERVER_NAME);
    }

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, new EurekaWriteServerRunner(SERVER_NAME));
    }
}
