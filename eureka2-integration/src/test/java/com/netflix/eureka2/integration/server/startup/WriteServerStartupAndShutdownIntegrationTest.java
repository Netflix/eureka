package com.netflix.eureka2.integration.server.startup;

import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaWriteServerRunner;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.netflix.eureka2.server.config.bean.BootstrapConfigBean.aBootstrapConfig;
import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static com.netflix.eureka2.server.config.bean.WriteServerConfigBean.aWriteServerConfig;

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
    public void testStartsWithExplicitConfig() throws Exception {
        WriteServerConfig config = aWriteServerConfig()
                .withInstanceInfoConfig(
                        anEurekaInstanceInfoConfig()
                                .withEurekaApplicationName(SERVER_NAME)
                                .withEurekaVipAddress(SERVER_NAME)
                                .build()
                )
                .withTransportConfig(
                        anEurekaServerTransportConfig()
                                .withHttpPort(0)
                                .withServerPort(0)
                                .withShutDownPort(0)
                                .withWebAdminPort(0)
                                .build()
                )
                .withClusterDiscoveryConfig(
                        anEurekaClusterDiscoveryConfig()
                                .withClusterAddresses(clusterAddresses)
                                .withClusterResolverType(EurekaClusterResolvers.ResolverType.Fixed)
                                .build()
                )
                .withBootstrapConfig(aBootstrapConfig().withBootstrapEnabled(false).build())
                .build();

        EurekaWriteServerRunner serverRunner = new EurekaWriteServerRunner(config);
        executeAndVerifyLifecycle(serverRunner, SERVER_NAME);
    }

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, new EurekaWriteServerRunner(SERVER_NAME));
    }
}
