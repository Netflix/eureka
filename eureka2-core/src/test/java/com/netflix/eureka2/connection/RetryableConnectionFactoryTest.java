package com.netflix.eureka2.connection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.channel.AbstractServiceChannel;
import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ServiceChannel;
import com.netflix.eureka2.channel.TestChannel;
import com.netflix.eureka2.channel.TestChannelFactory;
import com.netflix.eureka2.metric.noop.NoOpStateMachineMetrics;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author David Liu
 */
public class RetryableConnectionFactoryTest {

    private static final Func1<TestFailableChannel, Observable<Void>> FAILABLE_CHANNEL_INPUT_FUN =
            new Func1<TestFailableChannel, Observable<Void>>() {
                @Override
                public Observable<Void> call(TestFailableChannel testChannel) {
                    return Observable.empty();
                }
            };

    private Subject<TestOp, TestOp> opSubject;
    private Observable<TestOp> opStream;
    private FailableChannelFactory innerFactory;
    private TestChannelFactory<TestFailableChannel> factory;
    private RetryableConnectionFactory<TestFailableChannel> handler;
    private TestSubscriber<Void> testSubscriber;
    private TestSubscriber<Void> initSubscriber;

    @Before
    public void setUp() {
        opSubject = ReplaySubject.create();
        opStream = opSubject.asObservable().distinctUntilChanged();
        innerFactory = new FailableChannelFactory();
        factory = new TestChannelFactory<>(innerFactory);
        handler = new RetryableConnectionFactory<>(factory);
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
        RetryableConnection<TestFailableChannel, Void> retryableConnection = handler.singleOpConnection(opStream, new TestConnectionFunc2(), FAILABLE_CHANNEL_INPUT_FUN);

        retryableConnection.getRetryableLifecycle().subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(0));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        opSubject.onNext(new TestOp(1));
        opSubject.onNext(new TestOp(2));

        testSubscriber.assertNoErrors();

        TestFailableChannel channel = factory.getLatestChannel();
        assertThat(channel, is(not(nullValue())));
        assertThat(channel.id, is(0));
        assertThat(channel.getDelegate().getResults().size(), is(3));

        int i = 0;
        for (WorkAndResult result : factory.getLatestChannel().getDelegate().getResults()) {
            assertThat(result.op.id, is(i++));
            assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        }

        assertThat(channel.getDelegate().isClosed(), is(false));  // no errors and no unsubscribed, so channel is not closed
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
        RetryableConnection<TestFailableChannel, Void> retryableConnection = handler.singleOpConnection(opStream, new TestConnectionFunc2(), FAILABLE_CHANNEL_INPUT_FUN);

        retryableConnection.getRetryableLifecycle().subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(0));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        opSubject.onNext(new TestOp(1));

        testSubscriber.assertNoErrors();

        TestFailableChannel channel = factory.getLatestChannel();
        assertThat(channel, is(not(nullValue())));
        assertThat(channel.id, is(0));
        assertThat(channel.getDelegate().getResults().size(), is(2));

        int i = 0;
        for (WorkAndResult result : factory.getLatestChannel().getDelegate().getResults()) {
            assertThat(result.op.id, is(i++));
            assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        }

        retryableConnection.close();

        assertThat(channel.getDelegate().isClosed(), is(true));
    }

    /**
     * onCompleted on the op stream should *not* close the latest channel. Only an explicit close() should do so.
     */
    @Test
    public void testConnectionLifecycleOnCompleted() {
        RetryableConnection<TestFailableChannel, Void> retryableConnection = handler.singleOpConnection(opStream, new TestConnectionFunc2(), FAILABLE_CHANNEL_INPUT_FUN);

        retryableConnection.getRetryableLifecycle().subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        opSubject.onNext(new TestOp(0));

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        opSubject.onNext(new TestOp(1));

        testSubscriber.assertNoErrors();

        TestFailableChannel channel = factory.getLatestChannel();
        assertThat(channel, is(not(nullValue())));
        assertThat(channel.id, is(0));
        assertThat(channel.getDelegate().getResults().size(), is(2));

        int i = 0;
        for (WorkAndResult result : factory.getLatestChannel().getDelegate().getResults()) {
            assertThat(result.op.id, is(i++));
            assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        }

        opSubject.onCompleted();
        testSubscriber.assertNoErrors();

        assertThat(channel.getDelegate().isClosed(), is(false));
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
        innerFactory = new FailableChannelFactory(new Func1<TestOp, Boolean>() {
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
        factory = new TestChannelFactory<>(innerFactory);

        handler = new RetryableConnectionFactory<>(factory);
        RetryableConnection<TestFailableChannel, Void> retryableConnection = handler.singleOpConnection(opStream, new TestConnectionFunc2(), FAILABLE_CHANNEL_INPUT_FUN);

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

        assertThat(factory.getAllChannels().size(), is(3));
        WorkAndResult result;

        TestFailableChannel channel0 = factory.getAllChannels().get(0);

        assertThat(channel0.id, is(0));
        assertThat(channel0.getDelegate().getResults().size(), is(1));
        result = channel0.getDelegate().getResults().get(0);
        assertThat(result.op.id, is(0));
        assertThat(result.result, is(WorkAndResult.Result.onError));

        TestFailableChannel channel1 = factory.getAllChannels().get(1);

        assertThat(channel1.id, is(1));
        assertThat(channel1.getDelegate().getResults().size(), is(2));
        result = channel1.getDelegate().getResults().get(0);
        assertThat(result.op.id, is(1));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        result = channel1.getDelegate().getResults().get(1);
        assertThat(result.op.id, is(2));
        assertThat(result.result, is(WorkAndResult.Result.onError));

        retryableConnection.close();
        for (TestFailableChannel c : factory.getAllChannels()) {
            assertThat(c.getDelegate().isClosed(), is(true));
        }
    }

    @Test
    public void testConnectionLifecycleOnErrorRetryInitialSuccess() {
        innerFactory = new FailableChannelFactory(new Func1<TestOp, Boolean>() {
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
        factory = new TestChannelFactory<>(innerFactory);

        handler = new RetryableConnectionFactory<>(factory);
        RetryableConnection<TestFailableChannel, Void> retryableConnection = handler.singleOpConnection(opStream, new TestConnectionFunc2(), FAILABLE_CHANNEL_INPUT_FUN);

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

        assertThat(factory.getAllChannels().size(), is(2));
        WorkAndResult result;

        TestFailableChannel channel0 = factory.getAllChannels().get(0);

        assertThat(channel0.id, is(0));
        assertThat(channel0.getDelegate().getResults().size(), is(2));
        result = channel0.getDelegate().getResults().get(0);
        assertThat(result.op.id, is(0));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        result = channel0.getDelegate().getResults().get(1);
        assertThat(result.op.id, is(1));
        assertThat(result.result, is(WorkAndResult.Result.onError));

        TestFailableChannel channel1 = factory.getAllChannels().get(1);

        assertThat(channel1.id, is(1));
        assertThat(channel1.getDelegate().getResults().size(), is(2));
        result = channel1.getDelegate().getResults().get(0);
        assertThat(result.op.id, is(2));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));
        result = channel1.getDelegate().getResults().get(1);
        assertThat(result.op.id, is(3));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));

        retryableConnection.close();
        for (TestFailableChannel c : factory.getAllChannels()) {
            assertThat(c.getDelegate().isClosed(), is(true));
        }
    }

    // since the nullary connection reuses the same constructs as the unary connection, just this test should suffice
    @Test
    public void testNullaryConnectionLifecycleOnErrorRetryInitialFail() {
        innerFactory = new FailableChannelFactory(new Func1<TestOp, Boolean>() {
            @Override
            public Boolean call(TestOp testOp) {
                switch (testOp.id) {
                    case 0:
                        return true;  // fail
                    default:
                        return false;
                }
            }
        });
        factory = new TestChannelFactory<>(innerFactory);

        handler = new RetryableConnectionFactory<>(factory);
        final AtomicInteger opCount = new AtomicInteger(0);
        RetryableConnection<TestFailableChannel, Void> retryableConnection = handler.zeroOpConnection(new TestConnectionFunc1(opCount));

        Observable<Void> connectionWithRetry = retryableConnection.getRetryableLifecycle()
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {  // do this at unsubscribe time as a retry action triggers unsubscribe -> (re)subscribe
                        opCount.getAndIncrement();
                    }
                })
                .retry(3);

        connectionWithRetry.subscribe(testSubscriber);
        retryableConnection.getInitObservable().subscribe(initSubscriber);

        initSubscriber.assertTerminalEvent();
        initSubscriber.assertNoErrors();

        assertThat(factory.getAllChannels().size(), is(2));
        WorkAndResult result;

        TestFailableChannel channel0 = factory.getAllChannels().get(0);

        assertThat(channel0.id, is(0));
        assertThat(channel0.getDelegate().getResults().size(), is(1));
        result = channel0.getDelegate().getResults().get(0);
        assertThat(result.op.id, is(0));
        assertThat(result.result, is(WorkAndResult.Result.onError));

        TestFailableChannel channel1 = factory.getAllChannels().get(1);

        assertThat(channel1.id, is(1));
        assertThat(channel1.getDelegate().getResults().size(), is(1));
        result = channel1.getDelegate().getResults().get(0);
        assertThat(result.op.id, is(1));
        assertThat(result.result, is(WorkAndResult.Result.onCompleted));

        retryableConnection.close();
        for (TestFailableChannel c : factory.getAllChannels()) {
            assertThat(c.getDelegate().isClosed(), is(true));
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
            if (this == o)
                return true;
            if (!(o instanceof TestOp))
                return false;

            TestOp testOp = (TestOp) o;

            if (id != testOp.id)
                return false;

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

    static class TestFailableChannel extends TestChannel<FailableChannel, TestOp> implements FailableChannel {

        public TestFailableChannel(FailableChannel delegate, Integer id) {
            super(delegate, id);
        }

        @Override
        public Observable<Void> doWork(TestOp op) {
            return delegate.doWork(op);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public List<WorkAndResult> getResults() {
            return delegate.getResults();
        }
    }

    interface FailableChannel extends ServiceChannel {
        public Observable<Void> doWork(TestOp op);

        public boolean isClosed();

        public List<WorkAndResult> getResults();
    }

    static class FailableChannelImpl extends AbstractServiceChannel<FailableChannelImpl.SimpleState> implements FailableChannel {
        public enum SimpleState {SimpleState}

        ;

        public final AtomicBoolean closed = new AtomicBoolean(false);
        public final ConcurrentLinkedQueue<WorkAndResult> results = new ConcurrentLinkedQueue<>();

        public final Func1<TestOp, Boolean> failurePredicate;

        protected FailableChannelImpl(Func1<TestOp, Boolean> failurePredicate) {
            super(SimpleState.SimpleState, new NoOpStateMachineMetrics<SimpleState>());
            this.failurePredicate = failurePredicate;
        }

        @Override
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
        protected void _close() {
            closed.set(true);
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public List<WorkAndResult> getResults() {
            return new ArrayList<>(results);
        }
    }

    static class FailableChannelFactory implements ChannelFactory<TestFailableChannel> {

        final AtomicInteger channelId = new AtomicInteger(0);
        final AtomicBoolean isShutdown = new AtomicBoolean(false);

        final Func1<TestOp, Boolean> failurePredicate;

        public FailableChannelFactory() {
            this(new Func1<TestOp, Boolean>() {
                @Override
                public Boolean call(TestOp testOp) {
                    return false;
                }
            });
        }

        public FailableChannelFactory(Func1<TestOp, Boolean> failurePredicate) {
            this.failurePredicate = failurePredicate;
        }

        @Override
        public TestFailableChannel newChannel() {
            if (isShutdown.get()) {
                return null;
            }

            FailableChannel delegate = new FailableChannelImpl(failurePredicate);
            return new TestFailableChannel(delegate, channelId.getAndIncrement());
        }

        @Override
        public void shutdown() {

        }
    }

    static class TestConnectionFunc2 implements Func2<TestFailableChannel, TestOp, Observable<Void>> {
        @Override
        public Observable<Void> call(TestFailableChannel testFailableChannel, TestOp testOp) {
            return testFailableChannel.doWork(testOp);
        }
    }

    static class TestConnectionFunc1 implements Func1<TestFailableChannel, Observable<Void>> {
        private final AtomicInteger opId;

        public TestConnectionFunc1(AtomicInteger opId) {
            this.opId = opId;
        }

        @Override
        public Observable<Void> call(TestFailableChannel testFailableChannel) {
            return testFailableChannel.doWork(new TestOp(opId.get()));
        }
    }

}
