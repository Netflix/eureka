package com.netflix.eureka2.integration.server.batching;

import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.integration.EurekaDeploymentClients;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.functions.Action1;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * @author Tomasz Bak
 */
@Category(IntegrationTest.class)
public class WriteServerNotificationBatchingTest {

    private static final int CLUSTER_SIZE = 50;

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(2, 0);

    private EurekaDeploymentClients eurekaDeploymentClients;

    @Before
    public void setUp() throws Exception {
        eurekaDeploymentClients = new EurekaDeploymentClients(eurekaDeploymentResource.getEurekaDeployment());
    }

    @Test
    public void testWriteServerReturnsAvailableContentAsOneBatch() throws Exception {
        EurekaInterestClient subscriberClient = eurekaDeploymentResource.interestClientToWriteServer(0);

        InstanceInfo instanceTemplate = SampleInstanceInfo.WebServer.build();
        eurekaDeploymentClients.fillUpRegistry(CLUSTER_SIZE, instanceTemplate);

        LinkedHashSet<InstanceInfo> batch = subscriberClient.forInterest(Interests.forApplications(instanceTemplate.getApp()))
                .doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        // Inject processing delay, to help expose potential batch marker races.
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignore) {
                        }
                    }
                })
                .compose(InterestFunctions.buffers())
                .compose(InterestFunctions.snapshots())
                .take(1)
                .timeout(30, TimeUnit.SECONDS)
                .toBlocking()
                .first();

        assertThat(batch.size(), is(equalTo(CLUSTER_SIZE)));
    }
}
