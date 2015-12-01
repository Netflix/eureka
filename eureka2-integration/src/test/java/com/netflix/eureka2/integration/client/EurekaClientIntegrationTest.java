package com.netflix.eureka2.integration.client;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClient.RegistrationStatus;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.internal.rx.RxBlocking;
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

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
@Category(IntegrationTest.class)
public class EurekaClientIntegrationTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = anEurekaDeploymentResource(1, 1).build();

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

        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withServerResolver(ServerResolvers.fromEureka(writeCluster.interestResolver())
                        .forInterest(Interests.forVips(readClusterVip)))
                .build();

        EurekaRegistrationClient registrationClient = eurekaDeploymentResource.registrationClientToWriteCluster();

        try {
            // First register
            InstanceInfo info = SampleInstanceInfo.ZuulServer.build();
            registrationClient.register(Observable.just(info)).subscribe();

            // Now check that we get the notification from the read server
            Observable<ChangeNotification<InstanceInfo>> notifications = interestClient
                    .forInterest(Interests.forVips(info.getVipAddress()))
                    .filter(dataOnlyFilter());
            Iterator<ChangeNotification<InstanceInfo>> notificationIt = RxBlocking.iteratorFrom(5, TimeUnit.HOURS, notifications);

            assertThat(notificationIt.next(), is(addChangeNotificationOf(info)));
        } finally {
            registrationClient.shutdown();
            interestClient.shutdown();
        }
    }

    @Test(timeout = 60000)
    @Ignore
    public void testResolveFromDns() throws Exception {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withServerResolver(ServerResolvers.fromDnsName("cluster.domain.name").withPort(12102))
                .build();

        try {
            ExtTestSubscriber<RegistrationStatus> testSubscriber = new ExtTestSubscriber<>();

            Observable<RegistrationStatus> result = registrationClient.register(Observable.just(SampleInstanceInfo.CliServer.build()));
            result.take(1).subscribe(testSubscriber);
            result.subscribe();  // start the registration

            testSubscriber.assertOnCompleted(10, TimeUnit.SECONDS);
        } finally {
            registrationClient.shutdown();
        }
    }
}
