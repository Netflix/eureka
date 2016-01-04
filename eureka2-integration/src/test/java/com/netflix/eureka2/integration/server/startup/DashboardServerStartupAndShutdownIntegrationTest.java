package com.netflix.eureka2.integration.server.startup;

import com.netflix.eureka2.EurekaDashboardRunner;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.model.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.netflix.eureka2.config.bean.EurekaDashboardConfigBean.aDashboardConfig;
import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerRegistryConfigBean.anEurekaServerRegistryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class DashboardServerStartupAndShutdownIntegrationTest extends AbstractStartupAndShutdownIntegrationTest<EurekaDashboardRunner> {

    public static final String SERVER_NAME = "dashboard-server-startupAndShutdown";

    @Test(timeout = 60000)
    public void testStartsWithExplicitConfig() throws Exception {
        EurekaDashboardConfig config = aDashboardConfig()
                .withRegistryConfig(
                        anEurekaServerRegistryConfig()
                                .withEvictionAllowedPercentageDrop(20)
                                .build()
                )
                .withInstanceInfoConfig(
                        anEurekaInstanceInfoConfig()
                                .withDataCenterResolveIntervalSec(30000)
                                .withDataCenterType(DataCenterType.Basic)
                                .withEurekaApplicationName(SERVER_NAME)
                                .withEurekaVipAddress(SERVER_NAME)
                                .build()
                )
                .withTransportConfig(
                        anEurekaServerTransportConfig()
                                .withConnectionAutoTimeoutMs(30 * 60 * 1000)
                                .withHeartbeatIntervalMs(30000)
                                .withHttpPort(0)
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

        EurekaDashboardRunner server = new EurekaDashboardRunner(config);
        executeAndVerifyLifecycle(server, SERVER_NAME);
    }

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, new EurekaDashboardRunner(SERVER_NAME));
    }
}