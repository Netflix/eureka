package com.netflix.eureka2.integration;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.registration.RegistrationRequest;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func2;
import rx.observers.TestSubscriber;
import rx.subjects.BehaviorSubject;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static com.netflix.eureka2.rx.RxBlocking.iteratorFrom;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotification;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.bufferingChangeNotification;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
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
    @Test(timeout = 60000)
    public void testWriteClusterReplicationWorksBothWays() throws Exception {
        EurekaClient clientToFirst = eurekaDeploymentResource.connectToWriteServer(0);
        EurekaClient clientToSecond = eurekaDeploymentResource.connectToWriteServer(1);

        // First -> Second
        testWriteClusterReplicationWorksBothWays(clientToFirst, clientToSecond, SampleInstanceInfo.DiscoveryServer.build());

        // Second <- First
        testWriteClusterReplicationWorksBothWays(clientToSecond, clientToFirst, SampleInstanceInfo.ZuulServer.build());

        clientToFirst.shutdown();
        clientToSecond.shutdown();
    }

    protected void testWriteClusterReplicationWorksBothWays(EurekaClient firstClient, EurekaClient secondClient, InstanceInfo clientInfo) throws Exception {
        // Register via first write server
        RegistrationRequest request = firstClient.connect(Observable.just(clientInfo));
        Subscription subscription = request.subscribe();
        request.getInitObservable().toBlocking().firstOrDefault(null);  // wait for initial registration

        // Subscribe to second write server
        Interest<InstanceInfo> interest = Interests.forApplications(clientInfo.getApp());
        Observable<ChangeNotification<InstanceInfo>> notifications = secondClient.forInterest(interest).filter(dataOnlyFilter());

        Iterator<ChangeNotification<InstanceInfo>> notificationIterator = iteratorFrom(60, TimeUnit.SECONDS, notifications);
        assertThat(notificationIterator.next(), is(addChangeNotificationOf(clientInfo)));

        // Now unregister
        subscription.unsubscribe();
        assertThat(notificationIterator.next(), is(deleteChangeNotificationOf(clientInfo)));
    }

    @Test(timeout = 60000)
    public void testWriteClusterReplicationWithRegistrationLifecycle() throws Exception {
        final EurekaClient registrationClient = eurekaDeploymentResource.connectToWriteServer(0);
        final EurekaClient discoveryClient = eurekaDeploymentResource.connectToWriteServer(1);

        InstanceInfo.Builder seedBuilder = new InstanceInfo.Builder().withId("id123").withApp("app");
        List<InstanceInfo> infos = Arrays.asList(
                seedBuilder.withAppGroup("AAA").build(),
                seedBuilder.withAppGroup("BBB").build(),
                seedBuilder.withAppGroup("CCC").build()
        );

        // Subscribe to second write server
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        Interest<InstanceInfo> interest = Interests.forApplications(infos.get(0).getApp());
        discoveryClient.forInterest(interest).filter(dataOnlyFilter()).subscribe(testSubscriber);

        // We need to wait for notification after each registry update, to avoid compaction
        // on the way.
        BehaviorSubject<InstanceInfo> registrant = BehaviorSubject.create();
        Subscription subscription = registrationClient.connect(registrant).subscribe();
        registrant.onNext(infos.get(0));
        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotificationOf(infos.get(0))));

        registrant.onNext(infos.get(1));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(1))));

        registrant.onNext(infos.get(2));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(2))));

        // do the unregister after we've looked at the register and updates. Otherwise the unregister may process
        // before replication happen which means no data will be replicated to the second write server.
        subscription.unsubscribe();
        assertThat(testSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(infos.get(2))));

        registrationClient.shutdown();
        discoveryClient.shutdown();
    }

    @Test(timeout = 60000)
    public void testSubscriptionToInterestChannelGetsAllUpdates() throws Exception {
        EurekaClient dataSourceClient = eurekaDeploymentResource.connectToWriteServer(0);
        EurekaClient subscriberClient = eurekaDeploymentResource.connectToWriteServer(0);

        Iterator<InstanceInfo> instanceInfos = SampleInstanceInfo.collectionOf("itest", SampleInstanceInfo.ZuulServer.build());

        // First populate registry with some data.
        InstanceInfo firstRecord = instanceInfos.next();
        dataSourceClient.connect(Observable.just(firstRecord)).subscribe();

        // Subscribe to get current registry content
        Observable<ChangeNotification<InstanceInfo>> notifications =
                subscriberClient.forInterest(Interests.forApplications(firstRecord.getApp())).filter(dataOnlyFilter());
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator = iteratorFrom(5, TimeUnit.SECONDS, notifications);

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(firstRecord)));

        // Now register another client
        InstanceInfo secondRecord = instanceInfos.next();
        dataSourceClient.connect(Observable.just(secondRecord)).subscribe();

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(secondRecord)));

        dataSourceClient.shutdown();
        subscriberClient.shutdown();
    }

    @Test
    public void testWriteServerReturnsAvailableContentAsOneBatch() throws Exception {
        EurekaClient subscriberClient = eurekaDeploymentResource.connectToWriteServer(0);

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        subscriberClient.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);

        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotification()));
        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotification()));
        assertThat(testSubscriber.takeNextOrWait(), is(bufferingChangeNotification()));

        subscriberClient.shutdown();
    }

}
