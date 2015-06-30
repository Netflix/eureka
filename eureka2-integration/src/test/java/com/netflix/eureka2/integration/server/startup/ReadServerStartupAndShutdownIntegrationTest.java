package com.netflix.eureka2.integration.server.startup;

import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaReadServerRunner;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerConfigBean.anEurekaServerConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * This test suite verifies that read server starts up successfully, given configuration
 * parameters either from file or command line explicitly.
 *
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class ReadServerStartupAndShutdownIntegrationTest extends AbstractStartupAndShutdownIntegrationTest<EurekaReadServerRunner> {

    public static final String SERVER_NAME = "read-server-startupAndShutdown";

    @Test(timeout = 60000)
    public void testStartsWithExplicitConfig() throws Exception {
        EurekaServerConfig config = anEurekaServerConfig()
                .withInstanceInfoConfig(
                        anEurekaInstanceInfoConfig()
                                .withEurekaApplicationName(SERVER_NAME)
                                .withEurekaVipAddress(SERVER_NAME)
                                .build()
                )
                .withTransportConfig(
                        anEurekaServerTransportConfig()
                                .withHttpPort(0)
                                .withInterestPort(0)
                                .withShutDownPort(0)
                                .withWebAdminPort(0)
                                .build()
                )
                .withClusterDiscoveryConfig(
                        anEurekaClusterDiscoveryConfig()
                                .withClusterAddresses(clusterAddresses)
                                .withClusterResolverType(ResolverType.Fixed)
                                .build()
                )
                .build();

        EurekaReadServerRunner server = new EurekaReadServerRunner(config);
        executeAndVerifyLifecycle(server, SERVER_NAME);
    }

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, new EurekaReadServerRunner(SERVER_NAME));
    }
}
