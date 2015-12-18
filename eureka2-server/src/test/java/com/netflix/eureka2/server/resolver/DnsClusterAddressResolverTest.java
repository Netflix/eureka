package com.netflix.eureka2.server.resolver;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.resolver.DnsEurekaClusterResolver.DnsReadServerClusterResolver;
import com.netflix.eureka2.server.resolver.DnsEurekaClusterResolver.DnsWriteServerClusterResolver;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.spi.transport.EurekaTransportFactory.DEFAULT_DISCOVERY_PORT;
import static com.netflix.eureka2.spi.transport.EurekaTransportFactory.DEFAULT_REGISTRATION_PORT;
import static com.netflix.eureka2.spi.transport.EurekaTransportFactory.DEFAULT_REPLICATION_PORT;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class DnsClusterAddressResolverTest {

    private final ReplaySubject<ChangeNotification<String>> dnsChangeNotificationSubject = ReplaySubject.create();

    private final ExtTestSubscriber<ChangeNotification<ClusterAddress>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        dnsChangeNotificationSubject.onNext(new ChangeNotification<String>(Kind.Add, "serverA"));
        dnsChangeNotificationSubject.onNext(new ChangeNotification<String>(Kind.Add, "serverB"));
    }

    @Test
    public void testWriteClusterServerResolution() throws Exception {
        DnsWriteServerClusterResolver resolver = new DnsWriteServerClusterResolver("eureka2.cluster.com",
                DEFAULT_REGISTRATION_PORT, DEFAULT_REGISTRATION_PORT, DEFAULT_REGISTRATION_PORT, Schedulers.computation()) {
            @Override
            protected Observable<ChangeNotification<String>> createDnsChangeNotificationSource(String domainName, Scheduler scheduler) {
                return dnsChangeNotificationSubject;
            }
        };
        serverResolutionTest(resolver);
    }

    @Test
    public void testReadClusterServerResolution() throws Exception {
        DnsReadServerClusterResolver resolver = new DnsReadServerClusterResolver("eureka2.cluster.com",
                DEFAULT_REGISTRATION_PORT, Schedulers.computation()) {
            @Override
            protected Observable<ChangeNotification<String>> createDnsChangeNotificationSource(String domainName, Scheduler scheduler) {
                return dnsChangeNotificationSubject;
            }
        };
        serverResolutionTest(resolver);
    }

    private void serverResolutionTest(DnsEurekaClusterResolver resolver) {
        resolver.clusterTopologyChanges().subscribe(testSubscriber);
        assertOnNext("serverA");
        assertOnNext("serverB");
    }

    private void assertOnNext(String server) {
        ChangeNotification<ClusterAddress> actual = testSubscriber.takeNextOrFail();
        assertThat(actual.getKind(), is(equalTo(Kind.Add)));
        assertThat(actual.getData().getHostName(), is(equalTo(server)));
        assertThat(actual.getData().getInterestPort(), is(equalTo(DEFAULT_REGISTRATION_PORT)));
    }
}