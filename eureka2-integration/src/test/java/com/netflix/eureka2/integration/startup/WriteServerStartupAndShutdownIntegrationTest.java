package com.netflix.eureka2.integration.startup;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.EurekaWriteServer;
import com.netflix.eureka2.server.config.EurekaCommonConfig.ResolverType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig.WriteServerConfigBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * This test suite verifies that write server starts up successfully, given configuration
 * parameters either from file or command line explicitly.
 *
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class WriteServerStartupAndShutdownIntegrationTest extends AbstractStartupAndShutdownIntegrationTest {

    public static final String SERVER_NAME = "write-server-startupAndShutdown";

    @Test
    public void testStartsWithFileBasedConfiguration() throws Exception {
        injectConfigurationValuesViaSystemProperties(SERVER_NAME);
        EurekaWriteServer server = new EurekaWriteServer(SERVER_NAME);
        executeAndVerifyLifecycle(server);
    }

    @Test
    public void testStartsWithCommandLineParameters() throws Exception {
        WriteServerConfig config = new WriteServerConfigBuilder()
                .withAppName(SERVER_NAME)
                .withResolverType(ResolverType.fixed)
                .withServerList(writeServerList)
                .build();
        EurekaWriteServer server = new EurekaWriteServer(config);
        executeAndVerifyLifecycle(server);
    }

    private void executeAndVerifyLifecycle(EurekaWriteServer server) throws Exception {
        server.start();

        // Subscribe to the other write node and verify that write server connected properly
        EurekaClient eurekaClient = eurekaDeploymentResource.connectToWriteServer(0);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forApplications(SERVER_NAME)).subscribe(testSubscriber);

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(Kind.Add)));

        // Shutdown write server
        sendShutdownCommand();
        server.waitTillShutdown();

        // Verify that write server registry entry is removed
        notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(Kind.Delete)));
    }
}
