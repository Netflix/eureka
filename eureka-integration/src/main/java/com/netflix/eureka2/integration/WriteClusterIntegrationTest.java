package com.netflix.eureka2.integration;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.netflix.eureka2.rx.RxBlocking.iteratorFrom;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * FIXME fix replication
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class WriteClusterIntegrationTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(2, 0);

    /**
     * This test verifies that the data are replicated in both ways between the two cluster nodes.
     * It verifies two cases where one of the nodes came up first (so had no peers first), and the
     * other joined afterwards (so was initialized with one peer already).
     */
    @Test(timeout = 10000)
    public void testWriteClusterReplicationWorksBothWays() throws Exception {
        EurekaClient clientToFirst = eurekaDeploymentResource.connectToWriteServer(0);
        EurekaClient clientToSecond = eurekaDeploymentResource.connectToWriteServer(1);

        // First -> Second
        testWriteClusterReplicationWorksBothWays(clientToFirst, clientToSecond, SampleInstanceInfo.DiscoveryServer.build());

        // Second <- First
        testWriteClusterReplicationWorksBothWays(clientToSecond, clientToFirst, SampleInstanceInfo.ZuulServer.build());
    }

    protected void testWriteClusterReplicationWorksBothWays(EurekaClient firstClient, EurekaClient secondClient, InstanceInfo clientInfo) {
        // Register via first write server
        firstClient.register(clientInfo).toBlocking().firstOrDefault(null);

        // Subscribe to second write server
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator =
                iteratorFrom(5, TimeUnit.SECONDS, secondClient.forApplication(clientInfo.getApp()));

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(clientInfo)));

        // Now unregister
        firstClient.unregister(clientInfo).toBlocking().firstOrDefault(null);

        assertThat(notificationIterator.next(), is(deleteChangeNotificationOf(clientInfo)));
    }

    @Test(timeout = 60000)
    public void testWriteClusterReplicationWithRegistrationLifecycle() throws Exception {
        final EurekaClient registrationClient = eurekaDeploymentResource.connectToWriteServer(0);
        final EurekaClient discoveryClient = eurekaDeploymentResource.connectToWriteServer(1);

        InstanceInfo.Builder seedBuilder = new InstanceInfo.Builder().withId("id").withApp("app");
        List<InstanceInfo> infos = Arrays.asList(
                seedBuilder.withAppGroup("AAA").build(),
                seedBuilder.withAppGroup("BBB").build(),
                seedBuilder.withAppGroup("CCC").build()
        );

        // Subscribe to second write server
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        discoveryClient.forApplication(infos.get(0).getApp()).subscribe(testSubscriber);

        // We need to block, otherwise if we shot all of them in one row, they may be
        // compacted in the index.
        registrationClient.register(infos.get(0)).toBlocking().firstOrDefault(null);
        registrationClient.register(infos.get(1)).toBlocking().firstOrDefault(null);
        registrationClient.register(infos.get(2)).toBlocking().firstOrDefault(null);
        registrationClient.unregister(infos.get(2)).toBlocking().firstOrDefault(null);

        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotificationOf(infos.get(0))));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(1))));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(2))));
        assertThat(testSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(infos.get(2))));

        registrationClient.close();
        discoveryClient.close();
    }

    @Test
    public void testSubscriptionToInterestChannelGetsAllUpdates() throws Exception {
        EurekaClient dataSourceClient = eurekaDeploymentResource.connectToWriteServer(0);
        EurekaClient subscriberClient = eurekaDeploymentResource.connectToWriteServer(0);

        Iterator<InstanceInfo> instanceInfos = SampleInstanceInfo.collectionOf("itest", SampleInstanceInfo.ZuulServer.build());

        // First populate registry with some data.
        InstanceInfo firstRecord = instanceInfos.next();
        dataSourceClient.register(firstRecord).toBlocking().firstOrDefault(null);

        // Subscribe to get current registry content
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator =
                iteratorFrom(5, TimeUnit.SECONDS, subscriberClient.forInterest(Interests.forApplications(firstRecord.getApp())));

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(firstRecord)));

        // Now register another client
        InstanceInfo secondRecord = instanceInfos.next();
        dataSourceClient.register(secondRecord).toBlocking().firstOrDefault(null);

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(secondRecord)));
    }
}
