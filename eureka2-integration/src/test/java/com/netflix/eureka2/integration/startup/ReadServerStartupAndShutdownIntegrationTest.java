package com.netflix.eureka2.integration.startup;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.EurekaReadServer;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ResolverType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig.EurekaServerConfigBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * This test suite verifies that read server starts up successfully, given configuration
 * parameters either from file or command line explicitly.
 *
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class ReadServerStartupAndShutdownIntegrationTest extends AbstractStartupAndShutdownIntegrationTest {

    public static final String SERVER_NAME = "read-server-startupAndShutdown";
    public static final int SHUTDOWN_PORT = 7704;  // note that this need to be in sync with what's in the property file

    @Test(timeout = 60000)
    public void testStartsWithCommandLineParameters() throws Exception {
        EurekaServerConfig config = new EurekaServerConfigBuilder()
                .withAppName(SERVER_NAME)
                .withResolverType(ResolverType.fixed)
                .withDiscoveryPort(0)  // use ephemeral port
                .withWebAdminPort(9079)
                .withShutDownPort(SHUTDOWN_PORT)
                .withServerList(writeServerList)
                .build();
        EurekaReadServer server = new EurekaReadServer(config);
        executeAndVerifyLifecycle(server);
    }

    private void executeAndVerifyLifecycle(EurekaReadServer server) throws Exception {
        server.start();

        // Subscribe to write cluster and verify that read server connected properly
        EurekaClient eurekaClient = eurekaDeploymentResource.connectToWriteCluster();

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forApplications(SERVER_NAME)).subscribe(testSubscriber);

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(Kind.Add)));

        // Shutdown read server
        sendShutdownCommand(SHUTDOWN_PORT);
        server.waitTillShutdown();

        // Verify that read server registry entry is removed
        notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(Kind.Delete)));
    }
}
