package com.netflix.eureka2.integration.random;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.integration.IntegrationTestClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.RandomTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

/**
 * @author David Liu
 */
@Category({IntegrationTest.class, RandomTest.class})
public class WriteServerRandomLifecycleTest extends AbstractRandomLifecycleTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 0);

    @Test(timeout = 60000)
    public void writeServerRandomLifecycleTest() {
        final EurekaInterestClient readClient = eurekaDeploymentResource.interestClientToWriteServer(0);
        final EurekaRegistrationClient writeClient = eurekaDeploymentResource.registrationClientToWriteServer(0);

        IntegrationTestClient testClient = new IntegrationTestClient(readClient, writeClient);

        List<ChangeNotification<InstanceInfo>> expectedLifecycle = testClient.getExpectedLifecycle();
        List<ChangeNotification<InstanceInfo>> actualLifecycle = testClient.playLifecycle();

        assertLifecycles(expectedLifecycle, actualLifecycle);

        readClient.shutdown();
        writeClient.shutdown();
    }
}
