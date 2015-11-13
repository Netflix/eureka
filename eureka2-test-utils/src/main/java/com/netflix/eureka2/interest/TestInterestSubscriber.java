package com.netflix.eureka2.interest;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test subscriber for accessing and running assertions on {@link ChangeNotification} streams.
 *
 * @author Tomasz Bak
 */
public class TestInterestSubscriber extends ExtTestSubscriber<ChangeNotification<StdInstanceInfo>> {

    @SafeVarargs
    public final void assertReceives(ChangeNotification<StdInstanceInfo>... dataNotifications) {
        for (ChangeNotification<StdInstanceInfo> n : dataNotifications) {
            assertThat(takeNextOrFail(), is(equalTo(n)));
        }
    }

    @SafeVarargs
    public final void assertReceivesBatch(ChangeNotification<StdInstanceInfo>... dataNotifications) {
        assertReceives(dataNotifications);
        assertThat(takeNextOrFail().getKind(), is(equalTo(Kind.BufferSentinel)));
    }
}
