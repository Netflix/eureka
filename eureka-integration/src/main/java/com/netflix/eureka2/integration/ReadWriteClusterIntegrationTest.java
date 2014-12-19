package com.netflix.eureka2.integration;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.testkit.junit.resources.ReadServerResource;
import com.netflix.eureka2.testkit.junit.resources.WriteServerResource;
import com.netflix.eureka2.testkit.junit.resources.EurekaClientResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import rx.observers.TestSubscriber;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author David Liu
 */
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

    @Test(timeout = 10000)
    public void testReadServerFetchesDataFromWriteServerRegistry() throws Exception {
        // Listen to interest stream updates
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator =
                RxBlocking.iteratorFrom(5, TimeUnit.SECONDS, eurekaClient.forInterest(Interests.forApplications(clientInfo.getApp())));

        TestSubscriber<Void> registrationSubscriber = new TestSubscriber<>();

        // Register
        eurekaClient.register(clientInfo).subscribe(registrationSubscriber);
        registrationSubscriber.awaitTerminalEvent(5, TimeUnit.SECONDS);
        registrationSubscriber.assertNoErrors();

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(clientInfo)));

        // Unregister
        eurekaClient.unregister(clientInfo).subscribe(registrationSubscriber);
        registrationSubscriber.awaitTerminalEvent(5, TimeUnit.SECONDS);
        registrationSubscriber.assertNoErrors();

        assertThat(notificationIterator.next(), is(deleteChangeNotificationOf(clientInfo)));
    }
}
