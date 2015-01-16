package com.netflix.eureka2.integration;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.EurekaClientResource;
import com.netflix.eureka2.testkit.junit.resources.ReadServerResource;
import com.netflix.eureka2.testkit.junit.resources.WriteServerResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import rx.observers.TestSubscriber;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class ReadWriteClusterIntegrationTest {

    public final WriteServerResource writeServerResource = new WriteServerResource();
    public final ReadServerResource readServerResource = new ReadServerResource(writeServerResource);
    public final EurekaClientResource eurekaClientResource =
            new EurekaClientResource("testClient", writeServerResource, readServerResource);

    @Rule
    public TestRule ruleChain = RuleChain.outerRule(writeServerResource)
            .around(readServerResource)
            .around(eurekaClientResource);

    private EurekaClient eurekaClient;
    private InstanceInfo clientInfo;

    @Before
    public void setUp() throws Exception {
        eurekaClient = eurekaClientResource.getEurekaClient();
        clientInfo = eurekaClientResource.getClientInfo();
    }

    @Test(timeout = 20000)
    public void testReadServerFetchesDataFromWriteServerRegistry() throws Exception {
        // Listen to interest stream updates
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forApplications(clientInfo.getApp())).subscribe(notificationSubscriber);

        // Register
        TestSubscriber<Void> registrationSubscriber = new TestSubscriber<>();

        eurekaClient.register(clientInfo).subscribe(registrationSubscriber);
        registrationSubscriber.awaitTerminalEvent(10, TimeUnit.SECONDS);
        registrationSubscriber.assertNoErrors();

        assertThat(notificationSubscriber.takeNextOrWait(), is(addChangeNotificationOf(clientInfo)));

        // Unregister
        registrationSubscriber = new TestSubscriber<>();
        eurekaClient.unregister(clientInfo).subscribe(registrationSubscriber);
        registrationSubscriber.awaitTerminalEvent(5, TimeUnit.SECONDS);
        registrationSubscriber.assertNoErrors();

        assertThat(notificationSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(clientInfo)));
    }
}
