package com.netflix.eureka2.integration.server.replication;

import com.netflix.eureka2.integration.EurekaDeploymentClients;
import com.netflix.eureka2.junit.categories.ExperimentalTest;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.testkit.netrouter.NetworkLink;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;

/**
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, ExperimentalTest.class})
public class EvictionInReplicationChannelTest {

    private static final int CLUSTER_SIZE = 20;

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource =
            anEurekaDeploymentResource(2, 0).withNetworkRouter(true).build();

    private EurekaDeployment eurekaDeployment;

    private EurekaDeploymentClients eurekaDeploymentClients;

    @Before
    public void setUp() throws Exception {
        eurekaDeployment = eurekaDeploymentResource.getEurekaDeployment();
        eurekaDeploymentClients = new EurekaDeploymentClients(eurekaDeployment);
    }

    /**
     * Disconnecting a replication channel and connecting it again should leave the system in
     * clean state with no stale registrations.
     */
    @Test
    public void testRegistryEvictionOnReplicationChannelReconnect() throws Exception {
        // Fill registry content of write server 1 and verify that server 0 has it
        InstanceInfo firstTemplate = SampleInstanceInfo.WebServer.build();
        eurekaDeploymentClients.fillUpRegistryOfServer(1, CLUSTER_SIZE, firstTemplate);
        eurekaDeploymentClients.verifyWriteServerRegistryContent(0, firstTemplate.getApp(), CLUSTER_SIZE);

        // Now simulate network failure
        NetworkLink replicationLink = eurekaDeployment.getNetworkRouter()
                .getLinkTo(eurekaDeployment.getWriteCluster().getServer(0).getReplicationPort());
        replicationLink.disconnect();

        InstanceInfo secondTemplate = SampleInstanceInfo.Backend.build();
        eurekaDeploymentClients.fillUpRegistryOfServer(1, CLUSTER_SIZE, secondTemplate);

        eurekaDeploymentClients.verifyWriteServerRegistryContent(0, firstTemplate.getApp(), CLUSTER_SIZE);
        eurekaDeploymentClients.verifyWriteServerHasNoInstance(0, secondTemplate.getApp());

        // Restore replication channel
        replicationLink.connect();

        eurekaDeploymentClients.verifyWriteServerRegistryContent(0, secondTemplate.getApp(), CLUSTER_SIZE);
        eurekaDeploymentClients.verifyWriteServerHasNoInstance(0, firstTemplate.getApp());
    }
}
