package com.netflix.eureka2.integration.client.mixedcodec;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import rx.Observable;
import rx.Subscription;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * 2 write servers and registration plus interest clients will cover all three protocols
 *
 * @author David Liu
 */
public abstract class AbstractMixedCodecTest {

    private final InstanceInfo registeringInfo = SampleInstanceInfo.CliServer.build();

    protected void doTest(EurekaRegistrationClient registrationClient, EurekaInterestClient interestClient) throws Exception {

        // Listen to interest stream updates
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();
        interestClient.forInterest(Interests.forApplications(registeringInfo.getApp()))
                .filter(dataOnlyFilter())
                .subscribe(notificationSubscriber);

        // Register
        Subscription subscription = registrationClient.register(Observable.just(registeringInfo)).subscribe();
        assertThat(notificationSubscriber.takeNextOrWait(), is(addChangeNotificationOf(registeringInfo)));

        // Unregister
        subscription.unsubscribe();
        assertThat(notificationSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(registeringInfo)));
    }

}
