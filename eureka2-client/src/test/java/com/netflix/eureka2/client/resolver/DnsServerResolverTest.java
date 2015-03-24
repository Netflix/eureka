package com.netflix.eureka2.client.resolver;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.Server;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author Tomasz Bak
 */
public class DnsServerResolverTest extends AbstractResolverTest {

    @Test(timeout = 10000)
    public void testReturnsDnsChangeUpdates() throws Exception {
        final PublishSubject<ChangeNotification<String>> dnsUpdatesSubject = PublishSubject.create();

        ServerResolver resolver = ServerResolvers
                .fromDnsName("my.domain")
                .configureDnsNameSource(dnsUpdatesSubject)
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
                new ChangeNotification<>(Kind.Add, "host3")
        );

        ServerResolver resolver = ServerResolvers
                .fromDnsName("my.domain")
                .configureDnsNameSource(dnsSource)
                .withPort(80);

        Set<Server> expected = asSet(new Server("host2", 80), new Server("host3", 80));
        Set<Server> actual = asSet(takeNext(resolver), takeNext(resolver), takeNext(resolver));

        assertThat(actual, is(equalTo(expected)));
    }

    @Test(timeout = 60000)
    public void testErrorLoadingDnsFallbackToCachedServerList() throws Exception {
        final AtomicInteger emitErrorCount = new AtomicInteger(3);
        final AtomicBoolean errorEmitted = new AtomicBoolean(false);
        final Observable<ChangeNotification<String>> dnsUpdateSource = Observable.create(new Observable.OnSubscribe<ChangeNotification<String>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<String>> subscriber) {
                if (emitErrorCount.getAndDecrement() > 0) {
                    subscriber.onNext(new ChangeNotification<>(Kind.Add, "host1"));
                    subscriber.onNext(new ChangeNotification<>(Kind.Add, "host2"));
                    subscriber.onCompleted();
                } else {
                    errorEmitted.set(true);
                    subscriber.onError(new Exception("fake unit test error"));
                }
            }
        });

        ServerResolver resolver = ServerResolvers
                .fromDnsName("my.domain")
                .configureDnsNameSource(dnsUpdateSource)
                .withPort(80);

        Collection<Server> resolvedServers = new HashSet<>();
        resolvedServers.add(resolver.resolve().toBlocking().firstOrDefault(null));
        resolvedServers.add(resolver.resolve().toBlocking().firstOrDefault(null));
        resolvedServers.add(resolver.resolve().toBlocking().firstOrDefault(null));

        assertThat(resolvedServers, containsInAnyOrder(new Server("host1", 80), new Server("host2", 80)));

        Server server1 = resolver.resolve().toBlocking().firstOrDefault(null);
        Server server2 = resolver.resolve().toBlocking().firstOrDefault(null);
        assertThat(server1, isIn(resolvedServers));
        assertThat(server2, isIn(resolvedServers));
        assertThat(server1, is(not(equalTo(server2))));
        assertThat(errorEmitted.get(), is(true));
    }
}