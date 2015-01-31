package com.netflix.eureka2.integration.random;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.integration.IntegrationTestClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
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
        final EurekaClient eurekaClient = eurekaDeploymentResource.connectToWriteServer(0);

        IntegrationTestClient testClient = new IntegrationTestClient(eurekaClient, eurekaClient);

        List<ChangeNotification<InstanceInfo>> expectedLifecycle = testClient.getExpectedLifecycle();
        List<ChangeNotification<InstanceInfo>> actualLifecycle = testClient.playLifecycle();

        assertLifecycles(expectedLifecycle, actualLifecycle);

        eurekaClient.close();
    }
}
