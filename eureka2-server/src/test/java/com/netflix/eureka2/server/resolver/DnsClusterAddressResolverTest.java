package com.netflix.eureka2.server.resolver;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.resolver.DnsEurekaClusterResolver.DnsEurekaServerClusterResolver;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

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
    public void testEurekaClusterServerResolution() throws Exception {
        DnsEurekaServerClusterResolver resolver = new DnsEurekaServerClusterResolver("eureka2.cluster.com",
                EurekaServerTransportConfig.DEFAULT_SERVER_PORT, Schedulers.computation()) {
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
        assertThat(actual.getData().getPort(), is(equalTo(EurekaServerTransportConfig.DEFAULT_SERVER_PORT)));
    }
}