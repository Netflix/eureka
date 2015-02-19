package com.netflix.eureka2.integration;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
@Category(IntegrationTest.class)
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
        eurekaClient.register(Observable.just(info)).subscribe();

        // Now check that we get the notification from the read server
        Observable<ChangeNotification<InstanceInfo>> notifications = eurekaClient
                .forInterest(Interests.forVips(info.getVipAddress()))
                .filter(dataOnlyFilter());
        Iterator<ChangeNotification<InstanceInfo>> notificationIt = RxBlocking.iteratorFrom(5, TimeUnit.HOURS, notifications);

        assertThat(notificationIt.next(), is(addChangeNotificationOf(info)));

        eurekaClient.shutdown();
    }

    @Test(timeout = 60000)
    @Ignore
    public void testResolveFromDns() {
        EurekaClient eurekaClient = Eureka.newClientBuilder(
                ServerResolvers.forDnsName("cluster.domain.name", 12103),
                ServerResolvers.forDnsName("cluster.domain.name", 12102)
        ).build();
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.register(Observable.just(SampleInstanceInfo.CliServer.build())).subscribe(testSubscriber);
    }
}
