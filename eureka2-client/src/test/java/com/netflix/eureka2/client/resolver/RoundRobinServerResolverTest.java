package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import junit.framework.Assert;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static junit.framework.Assert.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author David Liu
 */
public class RoundRobinServerResolverTest extends AbstractResolverTest {

    private TestSubscriber<Server> testSubscriber = new TestSubscriber<>();

    @Test
    public void testEmptyLoadBalancer() {
        ServerResolver resolver = new RoundRobinServerResolver()
                .withWarmUpConfiguration(10, TimeUnit.MILLISECONDS);

        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent(50, TimeUnit.MILLISECONDS);

        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnErrorEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().size(), is(0));
    }

    @Test
    public void testSingleElementLoadBalancer() throws Exception {
        ServerResolver resolver = new RoundRobinServerResolver(SERVER_A);

        ExtTestSubscriber<Server> extTestSubscriber = new ExtTestSubscriber<>();
        resolver.resolve().subscribe(extTestSubscriber);
        Server next = extTestSubscriber.takeNext(1000, TimeUnit.MILLISECONDS);

        assertThat(next, is(SERVER_A));

        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent(5000, TimeUnit.MILLISECONDS);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0), is(SERVER_A));
    }

    @Test
    public void testLoadBalancerBatchUpdates() {
        final List<ChangeNotification<Server>> batchOne = Arrays.asList(
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A),
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_B),
                ChangeNotification.<Server>bufferSentinel()
        );

        final List<ChangeNotification<Server>> batchTwo = Arrays.asList(
                new ChangeNotification<>(ChangeNotification.Kind.Delete, SERVER_A),
                new ChangeNotification<>(ChangeNotification.Kind.Delete, SERVER_B),
                new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_C)
        );

        final AtomicBoolean updated = new AtomicBoolean(false);

        Observable<ChangeNotification<Server>> serverSource = Observable.create(new Observable.OnSubscribe<ChangeNotification<Server>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<Server>> subscriber) {
                if (updated.compareAndSet(false, true)) {
                    Observable.from(batchOne).subscribe(subscriber);
                } else {
                    Observable.from(batchTwo).subscribe(subscriber);
                }
            }
        });

        ServerResolver resolver = new RoundRobinServerResolver(serverSource);

        // resolve once
        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0), isIn(Arrays.asList(SERVER_A, SERVER_B)));

        // resolve twice
        testSubscriber = new TestSubscriber<>();
        resolver.resolve().subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0), isIn(Arrays.asList(SERVER_C)));
    }

    @Test
    public void testLoadBalancerWarmUpTooLong() throws Exception {
        int warmUpTimeout = 500;
        int delayTime = warmUpTimeout + 400;
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // data delay > warmUp
        Observable<ChangeNotification<Server>> serverSource = Observable
                .just(new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A))
                .delay(delayTime, timeUnit);

        ServerResolver resolver = new RoundRobinServerResolver(serverSource)
                .withWarmUpConfiguration(warmUpTimeout, timeUnit);

        ExtTestSubscriber<Server> extTestSubscriber = new ExtTestSubscriber<>();
        resolver.resolve().subscribe(extTestSubscriber);

        try {
            extTestSubscriber.takeNext(warmUpTimeout + 100, TimeUnit.MILLISECONDS);
            fail("Exception from onError completed stream expected");
        } catch (Exception e) {
            extTestSubscriber.assertOnError();
        }
    }

    @Test
    public void testLoadBalancerWarmUpJustUnder() throws Exception {
        int warmUpTimeout = 500;
        int delayTime = warmUpTimeout - 200;
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        // data delay > warmUp
        Observable<ChangeNotification<Server>> serverSource = Observable
                .just(new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A))
                .delay(delayTime, timeUnit);

        ServerResolver resolver = new RoundRobinServerResolver(serverSource)
                .withWarmUpConfiguration(warmUpTimeout, timeUnit);

        ExtTestSubscriber<Server> extTestSubscriber = new ExtTestSubscriber<>();
        resolver.resolve().subscribe(extTestSubscriber);

        Server next = extTestSubscriber.takeNext(warmUpTimeout + 100, TimeUnit.MILLISECONDS);
        assertThat(next, is(SERVER_A));
        extTestSubscriber.assertOnCompleted(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testLoadBalancerInputStreamOnError() {
        Throwable expected = new Exception("test error");
        Observable<ChangeNotification<Server>> serverSource = Observable.error(expected);

        ServerResolver resolver = new RoundRobinServerResolver(serverSource);

        resolver.resolve().subscribe(testSubscriber);
        // return empty lb error instead
        assertThat(testSubscriber.getOnErrorEvents().get(0), is(instanceOf(NoSuchElementException.class)));
    }

    @Test
    public void testLoadBalancerInputStreamOnErrorWithSomeData() throws Exception {
        Throwable expected = new Exception("test error");
        Observable<ChangeNotification<Server>> errorSource = Observable.error(expected);
        Observable<ChangeNotification<Server>> serverSource = Observable
                .just(new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A))
                .concatWith(errorSource);

        ServerResolver resolver = new RoundRobinServerResolver(serverSource);

        resolver.resolve().subscribe(testSubscriber);
        assertThat(testSubscriber.getOnNextEvents().size(), is(0));  // when input onError we don't emit buffers
        testSubscriber.assertTerminalEvent();
        // return empty lb error instead
        assertThat(testSubscriber.getOnErrorEvents().get(0), is(instanceOf(NoSuchElementException.class)));
    }

    // this test is different from above in that it test two resolve ops, first successful and second input
    // onError immediately.
    @Test
    public void testLoadBalancerFallbackToPreviousIfOnError() throws Exception {
        PublishSubject<ChangeNotification<Server>> serverSubject = PublishSubject.create();

        ServerResolver resolver = new RoundRobinServerResolver(serverSubject);

        ExtTestSubscriber<Server> extTestSubscriber = new ExtTestSubscriber<>();

        resolver.resolve().subscribe(extTestSubscriber);
        serverSubject.onNext(new ChangeNotification<>(ChangeNotification.Kind.Add, SERVER_A));
        serverSubject.onNext(ChangeNotification.<Server>bufferSentinel());

        Server next = extTestSubscriber.takeNext();
        assertThat(next, is(SERVER_A));
        extTestSubscriber.assertOnCompleted(200, TimeUnit.MILLISECONDS);

        extTestSubscriber = new ExtTestSubscriber<>();
        resolver.resolve().subscribe(extTestSubscriber);
        Throwable expected = new Exception("test error");
        serverSubject.onError(expected);

        next = extTestSubscriber.takeNext();
        assertThat(next, is(SERVER_A));
        extTestSubscriber.assertOnCompleted(200, TimeUnit.MILLISECONDS);  // onComplete with older data
    }

    @Test
    public void testResolverRoundRobin() {
        Set<Server> fullServerSet = asSet(SERVER_A, SERVER_B, SERVER_C);

        ServerResolver resolver = new RoundRobinServerResolver(SERVER_A, SERVER_B, SERVER_C);

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
