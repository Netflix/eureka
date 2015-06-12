package com.netflix.eureka2.integration.server.interest;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static com.netflix.eureka2.rx.RxBlocking.iteratorFrom;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class WriteClusterInterestTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(2, 0);

    @Test(timeout = 60000)
    public void testSubscriptionToInterestChannelGetsAllUpdates() throws Exception {
        final EurekaRegistrationClient dataSourceClient = eurekaDeploymentResource.registrationClientToWriteServer(0);
        final EurekaInterestClient subscriberClient = eurekaDeploymentResource.interestClientToWriteServer(0);

        Iterator<InstanceInfo> instanceInfos = SampleInstanceInfo.collectionOf("itest", SampleInstanceInfo.ZuulServer.build());

        // First populate registry with some data.
        InstanceInfo firstRecord = instanceInfos.next();
        dataSourceClient.register(Observable.just(firstRecord)).subscribe();

        // Subscribe to get current registry content
        Observable<ChangeNotification<InstanceInfo>> notifications =
                subscriberClient.forInterest(Interests.forApplications(firstRecord.getApp())).filter(dataOnlyFilter());
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator = iteratorFrom(5, TimeUnit.SECONDS, notifications);

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(firstRecord)));

        // Now register another client
        InstanceInfo secondRecord = instanceInfos.next();
        dataSourceClient.register(Observable.just(secondRecord)).subscribe();

        assertThat(notificationIterator.next(), is(addChangeNotificationOf(secondRecord)));

        dataSourceClient.shutdown();
        subscriberClient.shutdown();
    }
}
