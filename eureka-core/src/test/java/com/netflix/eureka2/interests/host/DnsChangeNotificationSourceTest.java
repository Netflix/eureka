package com.netflix.eureka2.interests.host;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.junit.categories.ExternalConnectionTest;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
@Category(ExternalConnectionTest.class)
public class DnsChangeNotificationSourceTest {
    @Test
    public void testPublicAddressResolution() throws Exception {
        // Google has a long list of addresses.
        DnsChangeNotificationSource resolver = new DnsChangeNotificationSource("google.com");

        ExtTestSubscriber<ChangeNotification<String>> testSubscriber = new ExtTestSubscriber<>();
        resolver.forInterest(null).subscribe(testSubscriber);

        // We expect at least two entries
        assertThat(testSubscriber.takeNext(30, TimeUnit.SECONDS), is(notNullValue()));
        assertThat(testSubscriber.takeNext(30, TimeUnit.SECONDS), is(notNullValue()));
    }

    @Test
    public void testLocalhost() throws Exception {
        DnsChangeNotificationSource resolver = new DnsChangeNotificationSource("localhost");

        ExtTestSubscriber<ChangeNotification<String>> testSubscriber = new ExtTestSubscriber<>();
        resolver.forInterest(null).take(1).subscribe(testSubscriber);

        assertThat(testSubscriber.takeNext(30, TimeUnit.SECONDS), is(equalTo(new ChangeNotification<String>(Kind.Add, "localhost"))));
    }
}