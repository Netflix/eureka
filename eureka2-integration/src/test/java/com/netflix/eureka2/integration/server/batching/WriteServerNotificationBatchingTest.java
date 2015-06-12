package com.netflix.eureka2.integration.server.batching;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotification;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.bufferingChangeNotification;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Tomasz Bak
 */
@Category(IntegrationTest.class)
public class WriteServerNotificationBatchingTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(2, 0);

    @Test
    public void testWriteServerReturnsAvailableContentAsOneBatch() throws Exception {
        EurekaInterestClient subscriberClient = eurekaDeploymentResource.interestClientToWriteServer(0);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        subscriberClient.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);

        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotification()));
        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotification()));
        assertThat(testSubscriber.takeNextOrWait(), is(bufferingChangeNotification()));

        subscriberClient.shutdown();
    }
}
