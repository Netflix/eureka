package com.netflix.eureka2.interest;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test subscriber for accessing and running assertions on {@link ChangeNotification} streams.
 *
 * @author Tomasz Bak
 */
public class TestInterestSubscriber extends ExtTestSubscriber<ChangeNotification<InstanceInfo>> {

    @SafeVarargs
    public final void assertReceives(ChangeNotification<InstanceInfo>... dataNotifications) {
        for (ChangeNotification<InstanceInfo> n : dataNotifications) {
            assertThat(takeNextOrFail(), is(equalTo(n)));
        }
    }

    @SafeVarargs
    public final void assertReceivesBatch(ChangeNotification<InstanceInfo>... dataNotifications) {
        assertReceives(dataNotifications);
        assertThat(takeNextOrFail().getKind(), is(equalTo(Kind.BufferSentinel)));
    }
}
