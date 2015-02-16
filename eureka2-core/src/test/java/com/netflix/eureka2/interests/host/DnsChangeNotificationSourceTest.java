package com.netflix.eureka2.interests.host;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.junit.categories.ExternalConnectionTest;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
@Category(ExternalConnectionTest.class)
public class DnsChangeNotificationSourceTest {
    @Test(timeout = 60000)
    public void testPublicAddressResolution() throws Exception {
        // Google has a long list of addresses.
        testWithDomainName("google.com", 2);
    }

    @Test(timeout = 60000)
    public void testPublicAddressResolutionWithCNAME() throws Exception {
        // aws.amazonaws.com is a cname
        testWithDomainName("aws.amazonaws.com", 1);
    }

    @Test(timeout = 60000)
    public void testLocalhost() throws Exception {
        DnsChangeNotificationSource resolver = new DnsChangeNotificationSource("localhost");

        ExtTestSubscriber<ChangeNotification<String>> testSubscriber = new ExtTestSubscriber<>();
        resolver.forInterest(null).take(1).subscribe(testSubscriber);

        assertThat(testSubscriber.takeNext(30, TimeUnit.SECONDS), is(equalTo(new ChangeNotification<String>(Kind.Add, "localhost"))));
    }


    private void testWithDomainName(String domainName, int expectedEntries) throws Exception {
        DnsChangeNotificationSource resolver = new DnsChangeNotificationSource(domainName);

        ExtTestSubscriber<ChangeNotification<String>> testSubscriber = new ExtTestSubscriber<>();
        resolver.forInterest(null).subscribe(testSubscriber);

        for (int i = 0; i < expectedEntries; i++) {
            ChangeNotification<String> notification = testSubscriber.takeNext(30, TimeUnit.SECONDS);
            assertThat(notification, is(not(nullValue())));
            assertThat(notification.getData(), is(not(nullValue())));
        }
    }
}
