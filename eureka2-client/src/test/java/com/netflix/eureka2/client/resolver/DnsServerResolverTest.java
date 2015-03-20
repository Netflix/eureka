package com.netflix.eureka2.client.resolver;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotificationSource;
import com.netflix.eureka2.Server;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action0;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class DnsServerResolverTest extends AbstractResolverTest {

    private final ChangeNotificationSource<String> dnsChangeNotificationSource = mock(ChangeNotificationSource.class);

    @Test(timeout = 10000)
    public void testBuilderWithoutDefaults() throws Exception {
        ServerResolver resolver = ServerResolvers
                .fromDnsName("my.domain")
                .configureReload(1000, 2000, TimeUnit.MILLISECONDS)
                .configureReloadScheduler(Schedulers.test())
                .withPort(80);

        assertThat(resolver, is(notNullValue()));
    }

    @Test(timeout = 10000)
    public void testBuilderWithDefaults() throws Exception {
        ServerResolver resolver = ServerResolvers
                .fromDnsName("my.domain")
                .withPort(80);

        assertThat(resolver, is(notNullValue()));
    }

    @Test(timeout = 10000)
    public void testReturnsDnsChangeUpdates() throws Exception {
        final PublishSubject<ChangeNotification<String>> dnsUpdatesSubject = PublishSubject.create();

        when(dnsChangeNotificationSource.forInterest(null)).thenReturn(dnsUpdatesSubject);

        ServerResolver resolver = ServerResolvers
                .fromDnsName("my.domain")
                .configureChangeNotificationSource(dnsChangeNotificationSource)
                .withPort(80);

        Server actual = takeNextWithEmits(resolver, new Action0() {
            @Override
            public void call() {
                dnsUpdatesSubject.onNext(new ChangeNotification<String>(Kind.Add, "host1"));
                dnsUpdatesSubject.onNext(ChangeNotification.<String>bufferSentinel());
            }
        });
        assertThat(actual, is(equalTo(new Server("host1", 80))));

        actual = takeNextWithEmits(resolver, new Action0() {
            @Override
            public void call() {
                dnsUpdatesSubject.onNext(new ChangeNotification<String>(Kind.Add, "host2"));
                dnsUpdatesSubject.onNext(ChangeNotification.<String>bufferSentinel());
            }
        });
        assertThat(actual, is(equalTo(new Server("host2", 80))));

        actual = takeNextWithEmits(resolver, new Action0() {
            @Override
            public void call() {
                dnsUpdatesSubject.onNext(new ChangeNotification<String>(Kind.Add, "host3"));
                dnsUpdatesSubject.onCompleted();
            }
        });
        assertThat(actual, is(equalTo(new Server("host3", 80))));
    }

    @Test(timeout = 60000)
    public void testWithLoadBalancer() throws Exception {
        Observable<ChangeNotification<String>> dnsSource = Observable.just(
                new ChangeNotification<>(Kind.Add, "host1"),
                new ChangeNotification<>(Kind.Delete, "host1"),
                new ChangeNotification<>(Kind.Add, "host2"),
                new ChangeNotification<>(Kind.Add, "host3"),
                ChangeNotification.<String>bufferSentinel()
        );

        when(dnsChangeNotificationSource.forInterest(null)).thenReturn(dnsSource);

        ServerResolver resolver = ServerResolvers
                .fromDnsName("my.domain")
                .configureChangeNotificationSource(dnsChangeNotificationSource)
                .withPort(80);

        Set<Server> expected = asSet(new Server("host2", 80), new Server("host3", 80));
        Set<Server> actual = asSet(takeNext(resolver), takeNext(resolver), takeNext(resolver));

        assertThat(actual, is(equalTo(expected)));
    }
}