package com.netflix.eureka2.integration.examples;

import com.netflix.eureka2.example.client.SimpleApp;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class SimpleAppIntegrationTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 1);

    private EmbeddedWriteCluster writeCluster;
    private EmbeddedReadCluster readCluster;

    @Before
    public void setup() {
        writeCluster = eurekaDeploymentResource.getEurekaDeployment().getWriteCluster();
        readCluster = eurekaDeploymentResource.getEurekaDeployment().getReadCluster();
    }

    @Test(timeout = 60000)
    public void testExampleRunsSuccessfully() throws Exception {
        SimpleApp sampleApp = new SimpleApp(
                "localhost",
                writeCluster.getServer(0).getRegistrationPort(),
                readCluster.getServer(0).getDiscoveryPort(),
                readCluster.getVip()
        );
        sampleApp.run();
    }
}