package com.netflix.eureka2.integration.server.batching;

import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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

        LinkedHashSet<InstanceInfo> batch = subscriberClient.forInterest(Interests.forFullRegistry())
                .compose(InterestFunctions.buffers())
                .compose(InterestFunctions.snapshots())
                .take(1)
                .timeout(30, TimeUnit.SECONDS)
                .toBlocking()
                .first();

        assertThat(batch.size(), is(equalTo(2)));
    }
}
