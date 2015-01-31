package com.netflix.eureka2.client.resolver;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.resolver.DnsServerResolver.DnsServerResolverBuilder;
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotificationSource;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class DnsServerResolverTest {

    private final ChangeNotificationSource<String> dnsChangeNotificationSource = mock(ChangeNotificationSource.class);

    private DnsServerResolverBuilder resolverBuilder;
    private final PublishSubject<ChangeNotification<String>> dnsUpdatesSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        resolverBuilder = new DnsServerResolverBuilder() {
            @Override
            protected ChangeNotificationSource<String> createDnsChangeNotificationSource() {
                return dnsChangeNotificationSource;
            }
        };

        when(dnsChangeNotificationSource.forInterest(null)).thenReturn(dnsUpdatesSubject);
    }

    @Test(timeout = 60000)
    public void testBuilderWithoutDefaults() throws Exception {
        DnsServerResolver resolver = resolverBuilder
                .withDomainName("my.domain")
                .withPort(80)
                .withIdleTimeout(1000)
                .withReloadInterval(2000)
                .withReloadUnit(TimeUnit.MILLISECONDS)
                .withLoadBalancerBuilder(new DefaultLoadBalancerBuilder<Server>(null))
                .withScheduler(Schedulers.test())
                .build();
        assertThat(resolver, is(notNullValue()));
    }

    @Test(timeout = 60000)
    public void testBuilderWithDefaults() throws Exception {
        DnsServerResolver resolver = resolverBuilder
                .withDomainName("my.domain")
                .withPort(80)
                .build();
        assertThat(resolver, is(notNullValue()));
    }

    @Test(timeout = 10000)
    public void testReturnsDnsChangeUpdates() throws Exception {
        DnsServerResolver resolver = resolverBuilder
                .withDomainName("my.domain")
                .withPort(80)
                .build();
        Observable<Server> resolveObservable = resolver.resolve();

        // Push one DNS entry; resolve should get it
        dnsUpdatesSubject.onNext(new ChangeNotification<String>(Kind.Add, "host1"));
        assertResolvesFinallyTo(resolveObservable, new Server("host1", 80));

        // Push second DNS entry
        dnsUpdatesSubject.onNext(new ChangeNotification<String>(Kind.Add, "host2"));
        assertResolvesFinallyTo(resolveObservable, new Server("host2", 80));

        // Remove first DNS entry
        dnsUpdatesSubject.onNext(new ChangeNotification<String>(Kind.Delete, "host1"));
        assertNeverResolvesTo(resolveObservable, new Server("host1", 80));
    }

    private static void assertResolvesFinallyTo(Observable<Server> resolveObservable, Server expected) {
        Server actual;
        do {
            ExtTestSubscriber<Server> testSubscriber = new ExtTestSubscriber<>();
            resolveObservable.subscribe(testSubscriber);
            actual = testSubscriber.takeNext();
        } while (!actual.equals(expected));
    }

    private static void assertNeverResolvesTo(Observable<Server> resolveObservable, Server notExpected) {
        // As we cannot define hard boundaries here, we make a few rounds to be sure ocelli removed our entry.
        for (int i = 0; i < 10; i++) {
            ExtTestSubscriber<Server> testSubscriber = new ExtTestSubscriber<>();
            resolveObservable.subscribe(testSubscriber);
            assertThat(testSubscriber.takeNext(), is(not(equalTo(notExpected))));
        }
    }
}