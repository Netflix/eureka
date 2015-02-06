package com.netflix.eureka2.integration;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.observers.TestSubscriber;

import static com.netflix.eureka2.interests.ChangeNotifications.batchMarkerFilter;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class ReadWriteClusterIntegrationTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(3, 6);

    private EurekaClient eurekaClient;
    private InstanceInfo registeringInfo;

    @Before
    public void setup() {
        eurekaClient = eurekaDeploymentResource.connectToEureka();
        registeringInfo = SampleInstanceInfo.CliServer.build();
    }

    @After
    public void tearDown() {
        eurekaClient.close();
    }

    @Test(timeout = 30000)
    public void testReadServerFetchesDataFromWriteServerRegistry() throws Exception {
        // Listen to interest stream updates
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forApplications(registeringInfo.getApp()))
                .filter(batchMarkerFilter())
                .subscribe(notificationSubscriber);

        // Register
        TestSubscriber<Void> registrationSubscriber = new TestSubscriber<>();

        eurekaClient.register(registeringInfo).subscribe(registrationSubscriber);
        registrationSubscriber.awaitTerminalEvent(10, TimeUnit.SECONDS);
        registrationSubscriber.assertNoErrors();

        assertThat(notificationSubscriber.takeNextOrWait(), is(addChangeNotificationOf(registeringInfo)));

        // Unregister
        registrationSubscriber = new TestSubscriber<>();
        eurekaClient.unregister(registeringInfo).subscribe(registrationSubscriber);
        registrationSubscriber.awaitTerminalEvent(5, TimeUnit.SECONDS);
        registrationSubscriber.assertNoErrors();

        assertThat(notificationSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(registeringInfo)));
    }
}
