package com.netflix.eureka2.integration.startup;

import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaReadServer;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig.EurekaServerConfigBuilder;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * This test suite verifies that read server starts up successfully, given configuration
 * parameters either from file or command line explicitly.
 *
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class ReadServerStartupAndShutdownIntegrationTest extends
        AbstractStartupAndShutdownIntegrationTest<EurekaServerConfig, EurekaReadServer> {

    public static final String SERVER_NAME = "read-server-startupAndShutdown";

    @Test(timeout = 60000)
    public void testStartsWithCommandLineParameters() throws Exception {
        EurekaServerConfig config = new EurekaServerConfigBuilder()
                .withAppName(SERVER_NAME)
                .withResolverType(ResolverType.Fixed)
                .withDiscoveryPort(0)  // use ephemeral port
                .withWebAdminPort(0)
                .withShutDownPort(0)
                .withServerList(writeServerList)
                .build();
        EurekaReadServer server = new EurekaReadServer(config);
        executeAndVerifyLifecycle(server, SERVER_NAME);
    }

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, new EurekaReadServer(SERVER_NAME));
    }
}
