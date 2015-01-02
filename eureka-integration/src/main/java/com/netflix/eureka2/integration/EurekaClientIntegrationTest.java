package com.netflix.eureka2.integration;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaClientIntegrationTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 1);

    private EurekaDeployment deployment;

    @Before
    public void setUp() throws Exception {
        deployment = eurekaDeploymentResource.getEurekaDeployment();
    }

    /**
     * This test verifies Eureka client bootstrap process where the read cluster server
     * list is first read from the write cluster, and given this information, the client connects
     * the interest channel to one of the provided read servers.
     */
    @Test(timeout = 60000)
    public void testReadServerClusterIsResolvedFromWriteCluster() {
        EmbeddedWriteCluster writeCluster = deployment.getWriteCluster();
        String readClusterVip = deployment.getReadCluster().getVip();

        EurekaClient eurekaClient = Eureka.newClientBuilder(
                ServerResolvers.fromWriteServer(writeCluster.discoveryResolver(), readClusterVip),
                writeCluster.registrationResolver()
        ).build();

        // First register
        InstanceInfo info = SampleInstanceInfo.ZuulServer.build();
        eurekaClient.register(info).toBlocking().singleOrDefault(null);

        // Now check that we get the notification from the read server
        Iterator<ChangeNotification<InstanceInfo>> notificationIt = RxBlocking.iteratorFrom(5, TimeUnit.HOURS, eurekaClient.forVips(info.getVipAddress()));

        assertThat(notificationIt.next(), is(addChangeNotificationOf(info)));
    }
}
