package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
public class ObservableServerResolverTest extends AbstractResolverTest {

    private TestSubscriber<Server> testSubscriber = new TestSubscriber<>();

    @Test
    public void testResolverEmpty() {
        ServerResolver resolver = new ObservableServerResolver(Observable.<ChangeNotification<Server>>empty());

        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
    }

    @Test
    public void testResolverSingleElement() {
        Observable<ChangeNotification<Server>> serverSource = Observable.just(
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A)
        );

        ServerResolver resolver = new ObservableServerResolver(serverSource);

        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(SERVER_A));
    }

    @Test
    public void testResolverMultipleElement() {
        Observable<ChangeNotification<Server>> serverSource = Observable.just(
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A),
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_B),
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_C)
        );

        ServerResolver resolver = new ObservableServerResolver(serverSource);

        // resolve A
        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(SERVER_A));

        // resolve B
        testSubscriber = new TestSubscriber<>();
        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(SERVER_B));

        // resolve C
        testSubscriber = new TestSubscriber<>();
        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(SERVER_C));
    }

    @Test
    public void testResolverRoundRobin() {
        Observable<ChangeNotification<Server>> serverSource = Observable.just(
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A),
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_B),
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_C)
        );

        Set<Server> fullServerSet = asSet(SERVER_A, SERVER_B, SERVER_C);

        ServerResolver resolver = new ObservableServerResolver(serverSource);

        Map<Server, AtomicInteger> roundRobinCount = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            Server server = takeNext(resolver);
            if (roundRobinCount.containsKey(server)) {
                roundRobinCount.get(server).incrementAndGet();
            } else {
                roundRobinCount.put(server, new AtomicInteger(1));
            }
        }

        assertThat(roundRobinCount.keySet(), is(equalTo(fullServerSet)));
        for (AtomicInteger count : roundRobinCount.values()) {
            assertThat(count.get(), is(2));
        }
    }
}
