package com.netflix.eureka2.integration;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;

import static com.netflix.eureka2.rx.RxBlocking.*;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author David Liu
 */
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
        testWriteClusterReplication(clientToFirst, clientToSecond, SampleInstanceInfo.DiscoveryServer.build());

        // Second <- First
        testWriteClusterReplication(clientToSecond, clientToFirst, SampleInstanceInfo.ZuulServer.build());
    }

    protected void testWriteClusterReplication(EurekaClient firstClient, EurekaClient secondClient, InstanceInfo clientInfo) {
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
