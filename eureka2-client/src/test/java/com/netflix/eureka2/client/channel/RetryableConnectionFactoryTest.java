package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ServiceChannel;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author David Liu
 */
public class RetryableConnectionFactoryTest {

    private Subject<TestOp, TestOp> opSubject;
    private Observable<TestOp> opStream;
    private TestChannelFactory factory;
    private TestConnectionFactory handler;
    private TestSubscriber<Void> testSubscriber;
    private TestSubscriber<Void> initSubscriber;

    @Before
    public void setUp() {
        opSubject = ReplaySubject.create();
        opStream = opSubject.asObservable().distinctUntilChanged();
        factory = new TestChannelFactory();
        handler = new TestConnectionFactory(factory);
        testSubscriber = new TestSubscriber<>();
        initSubscriber = new TestSubscriber<>();
    }

    /**
     * Happy case:
     * 1. create a new channel from factory
     * 2. connect to operation data stream
     * 3. perform first operation on the channel
     * 4. perform subsequent operations on the channel
     */
    @Test
    public void testHappyCase() throws Exception {
        RetryableConnection<TestServiceChannel> retryableConnection = handler.newConnection(opStream);

        retryableConnection.getRetryableLifecycle().subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(0));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        opSubject.onNext(new TestOp(1));
        opSubject.onNext(new TestOp(2));

        testSubscriber.assertNoErrors();

        TestServiceChannel channel = factory.getLatestChannel();
        assertThat(channel, is(not(nullValue())));
        assertThat(channel.id, is(0));
        assertThat(channel.results.size(), is(3));

        int i = 0;
        for (WorkAndResult result : factory.getLatestChannel().results) {
            assertThat(result.op.id, is(i++));
            assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        }

        assertThat(channel.closed.get(), is(false));  // no errors and no unsubscribed, so channel is not closed
        assertThat(opSubject.hasObservers(), is(true));
    }

    /**
     * close() from the retryable connection should:
     * 1. unsubscribe to the dataStream
     * 2. OnComplete to the dataStreamSubject
     * 3. close the channel(s)
     */
    @Test
    public void testClose() {
        RetryableConnection<TestServiceChannel> retryableConnection = handler.newConnection(opStream);

        retryableConnection.getRetryableLifecycle().subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(0));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        opSubject.onNext(new TestOp(1));

        testSubscriber.assertNoErrors();

        TestServiceChannel channel = factory.getLatestChannel();
        assertThat(channel, is(not(nullValue())));
        assertThat(channel.id, is(0));
        assertThat(channel.results.size(), is(2));

        int i = 0;
        for (WorkAndResult result : factory.getLatestChannel().results) {
            assertThat(result.op.id, is(i++));
            assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        }

        retryableConnection.close();

        assertThat(channel.closed.get(), is(true));
    }

    /**
     * onCompleted on the op stream should *not* close the latest channel. Only an explicit close() should do so.
     */
    @Test
    public void testConnectionLifecycleOnCompleted() {
        RetryableConnection<TestServiceChannel> retryableConnection = handler.newConnection(opStream);

        retryableConnection.getRetryableLifecycle().subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(0));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        opSubject.onNext(new TestOp(1));

        testSubscriber.assertNoErrors();

        TestServiceChannel channel = factory.getLatestChannel();
        assertThat(channel, is(not(nullValue())));
        assertThat(channel.id, is(0));
        assertThat(channel.results.size(), is(2));

        int i = 0;
        for (WorkAndResult result : factory.getLatestChannel().results) {
            assertThat(result.op.id, is(i++));
            assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        }

        opSubject.onCompleted();
        testSubscriber.assertNoErrors();

        assertThat(channel.closed.get(), is(false));
    }

    /**
     * When the channel lifecycle onError and retry is available, the connect observable should:
     * 1. create a new channel from factory
     * 2. (not) connect to the operation data stream as it should still be subscribed to by the data subject
     * 3. get latest data state from the data subject on new channel and reconnect
     * 3a. ^ this should also refresh the initObservable?
     */
    @Test
    public void testConnectionLifecycleOnErrorRetryInitialFail() {
        factory = new TestChannelFactory(new Func1<TestOp, Boolean>() {
            @Override
            public Boolean call(TestOp testOp) {
                switch (testOp.id) {
                    case 0:
                        return true;  // fail
                    case 1:
                        return false;
                    case 2:
                        return true;  // fail
                    default:
                        return false;
                }
            }
        });

        handler = new TestConnectionFactory(factory);
        RetryableConnection<TestServiceChannel> retryableConnection = handler.newConnection(opStream);

        final AtomicInteger opCount = new AtomicInteger(0);
        Observable<Void> connectionWithRetry = retryableConnection.getRetryableLifecycle()
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {  // do this at unsubscribe time as a retry action triggers unsubscribe -> (re)subscribe
                        opSubject.onNext(new TestOp(opCount.getAndIncrement()));
                    }
                })
                .retry(3);

        connectionWithRetry.subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(opCount.getAndIncrement()));
        opSubject.onNext(new TestOp(opCount.getAndIncrement()));
        opSubject.onNext(new TestOp(opCount.getAndIncrement()));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        assertThat(factory.allChannels.size(), is(3));
        WorkAndResult result;

        TestServiceChannel channel0 = factory.allChannels.poll();

        assertThat(channel0.id, is(0));
        assertThat(channel0.results.size(), is(1));
        result = channel0.results.poll();
        assertThat(result.op.id, is(0));
        assertThat(result.result, is(WorkAndResult.Result.onError));

        TestServiceChannel channel1 = factory.allChannels.poll();

        assertThat(channel1.id, is(1));
        assertThat(channel1.results.size(), is(2));
        result = channel1.results.poll();
        assertThat(result.op.id, is(1));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        result = channel1.results.poll();
        assertThat(result.op.id, is(2));
        assertThat(result.result, is(WorkAndResult.Result.onError));

        retryableConnection.close();
        for (TestServiceChannel c : factory.allChannels) {
            assertThat(c.closed.get(), is(true));
        }
    }

    @Test
    public void testConnectionLifecycleOnErrorRetryInitialSuccess() {
        factory = new TestChannelFactory(new Func1<TestOp, Boolean>() {
            @Override
            public Boolean call(TestOp testOp) {
                switch (testOp.id) {
                    case 0:
                        return false;
                    case 1:
                        return true;  // fail
                    default:
                        return false;
                }
            }
        });

        handler = new TestConnectionFactory(factory);
        RetryableConnection<TestServiceChannel> retryableConnection = handler.newConnection(opStream);

        final AtomicInteger opCount = new AtomicInteger(0);
        Observable<Void> connectionWithRetry = retryableConnection.getRetryableLifecycle()
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {  // do this at unsubscribe time as a retry action triggers unsubscribe -> (re)subscribe
                        opSubject.onNext(new TestOp(opCount.getAndIncrement()));
                    }
                })
                .retry(3);

        connectionWithRetry.subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(opCount.getAndIncrement()));
        opSubject.onNext(new TestOp(opCount.getAndIncrement()));
        opSubject.onNext(new TestOp(opCount.getAndIncrement()));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        assertThat(factory.allChannels.size(), is(2));
        WorkAndResult result;

        TestServiceChannel channel0 = factory.allChannels.poll();

        assertThat(channel0.id, is(0));
        assertThat(channel0.results.size(), is(2));
        result = channel0.results.poll();
        assertThat(result.op.id, is(0));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        result = channel0.results.poll();
        assertThat(result.op.id, is(1));
        assertThat(result.result, is(WorkAndResult.Result.onError));

        TestServiceChannel channel1 = factory.allChannels.poll();

        assertThat(channel1.id, is(1));
        assertThat(channel1.results.size(), is(2));
        result = channel1.results.poll();
        assertThat(result.op.id, is(2));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        result = channel1.results.poll();
        assertThat(result.op.id, is(3));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));

        retryableConnection.close();
        for (TestServiceChannel c : factory.allChannels) {
            assertThat(c.closed.get(), is(true));
        }
    }


    //
    // some test constructs
    //

    static class TestOp {
        public final int id;

        public TestOp(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Op:" + id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestOp)) return false;

            TestOp testOp = (TestOp) o;

            if (id != testOp.id) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    static class WorkAndResult {
        public enum Result {onCompleted, onError}

        public final TestOp op;
        public final Result result;

        public WorkAndResult(TestOp op, Result result) {
            this.op = op;
            this.result = result;
        }

        @Override
        public String toString() {
            return "WorkAndResult{" +
                    "op=" + op +
                    ", result=" + result +
                    '}';
        }
    }

    static class TestServiceChannel implements ServiceChannel {
        public final int id;
        public final Func1<TestOp, Boolean> failurePredicate;

        public final ConcurrentLinkedQueue<WorkAndResult> results = new ConcurrentLinkedQueue<>();
        public final ReplaySubject<Void> channelLifecycle = ReplaySubject.create();
        public final AtomicBoolean closed = new AtomicBoolean(false);

        public TestServiceChannel(int id, Func1<TestOp, Boolean> failurePredicate) {
            this.id = id;
            this.failurePredicate = failurePredicate;
        }

        public Observable<Void> doWork(TestOp op) {
            if (closed.get()) {
                return Observable.error(new Exception("already closed"));
            }

            if (failurePredicate.call(op)) {
                results.add(new WorkAndResult(op, WorkAndResult.Result.onError));
                Exception e = new Exception("error triggered");
                close(e);
                return Observable.error(e);
            } else {
                results.add(new WorkAndResult(op, WorkAndResult.Result.onCompleted));
                return Observable.empty();
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                channelLifecycle.onCompleted();
            }
        }

        @Override
        public void close(Throwable error) {
            if (closed.compareAndSet(false, true)) {
                channelLifecycle.onError(error);
            }
        }

        @Override
        public Observable<Void> asLifecycleObservable() {
            return channelLifecycle.asObservable();
        }

        @Override
        public String toString() {
            return "channel:" + id;
        }
    }

    static class TestChannelFactory implements ChannelFactory<TestServiceChannel> {

        public final ConcurrentLinkedQueue<TestServiceChannel> allChannels = new ConcurrentLinkedQueue<>();

        final AtomicReference<TestServiceChannel> latestChannel = new AtomicReference<>();
        final AtomicInteger channelId = new AtomicInteger(0);
        final AtomicBoolean isShutdown = new AtomicBoolean(false);

        final Func1<TestOp, Boolean> failurePredicate;

        public TestChannelFactory() {
            this(new Func1<TestOp, Boolean>() {
                @Override
                public Boolean call(TestOp testOp) {
                    return false;
                }
            });
        }

        public TestChannelFactory(Func1<TestOp, Boolean> failurePredicate) {
            this.failurePredicate = failurePredicate;
        }

        @Override
        public TestServiceChannel newChannel() {
            if (isShutdown.get()) {
                return null;
            }

            TestServiceChannel channel = new TestServiceChannel(channelId.getAndIncrement(), failurePredicate);
            allChannels.add(channel);
            latestChannel.set(channel);
            return channel;
        }

        public TestServiceChannel getLatestChannel() {
            return latestChannel.get();
        }

        @Override
        public void shutdown() {
            isShutdown.set(true);
        }
    }

    static class TestConnectionFactory extends RetryableConnectionFactory<TestServiceChannel, TestOp> {

        public TestConnectionFactory(ChannelFactory<TestServiceChannel> channelFactory) {
            super(channelFactory);
        }

        @Override
        protected Observable<Void> executeOnChannel(TestServiceChannel channel, TestOp state) {
            return channel.doWork(state);
        }
    }
}
