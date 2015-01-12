package com.netflix.eureka2.integration;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.integration.categories.IntegrationTest;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.rx.RxBlocking.*;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * FIXME getting interest is flaky
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class WriteServerIntegrationTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 0);


    @Test(timeout = 60000)
    public void testRegistrationLifecycle() throws Exception {
        final EurekaClient registrationClient = eurekaDeploymentResource.connectToWriteServer(0);
        final EurekaClient discoveryClient = eurekaDeploymentResource.connectToWriteServer(0);

        InstanceInfo.Builder seedBuilder = new InstanceInfo.Builder().withId("id").withApp("app");
        List<InstanceInfo> infos = Arrays.asList(
                seedBuilder.withAppGroup("AAA").build(),
                seedBuilder.withAppGroup("BBB").build(),
                seedBuilder.withAppGroup("CCC").build()
        );

        registrationClient.register(infos.get(0)).subscribe();
        registrationClient.register(infos.get(1)).subscribe();
        registrationClient.register(infos.get(2)).subscribe();
        registrationClient.unregister(infos.get(2)).subscribe();

        // Subscribe to second write server
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator =
                iteratorFrom(10, TimeUnit.SECONDS, discoveryClient.forApplication(infos.get(0).getApp()));

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(infos.get(0))));
        assertThat(notificationIterator.next(), is(modifyChangeNotificationOf(infos.get(1))));
        assertThat(notificationIterator.next(), is(modifyChangeNotificationOf(infos.get(2))));
        assertThat(notificationIterator.next(), is(deleteChangeNotificationOf(infos.get(2))));

        registrationClient.close();
        discoveryClient.close();
    }
}
