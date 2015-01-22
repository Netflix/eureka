/*
* Copyright 2014 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.netflix.eureka2.channel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.metric.noop.NoOpStateMachineMetrics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author David Liu
 */
public class RetryableServiceChannelTest {

    private static final long INITIAL_DELAY = 1000;
    private static final long MAX_DELAY = RetryableServiceChannel.MAX_EXP_BACK_OFF_MULTIPLIER * INITIAL_DELAY;

    private final TestScheduler scheduler = Schedulers.test();

    private Func0<TestChannel> channelFactory;
    private RetryableTestChannel retryableChannel;
    private TestChannel initialDelegate;

    @Before
    public void setUp() throws Exception {
        this.channelFactory = spy(new Func0<TestChannel>() {
            private AtomicInteger delegateChannelIdGenerator = new AtomicInteger(0);

            @Override
            public TestChannel call() {
                return spy(new TestChannel(delegateChannelIdGenerator.getAndIncrement()));
            }
        });

        retryableChannel = spy(new RetryableTestChannel(channelFactory, INITIAL_DELAY, scheduler));
        initialDelegate = retryableChannel.currentDelegateChannel();
        assertThat(initialDelegate.myId, equalTo(0));
        assertThat(retryableChannel.reestablishAttemptCount.get(), equalTo(0));
        assertThat(retryableChannel.reestablishSuccessCount.get(), equalTo(0));
        assertThat(retryableChannel.retryCount.get(), equalTo(0));
    }

    @After
    public void tearDown() throws Exception {
        retryableChannel.close();
    }


    @Test
    public void testDelegateChannelDisconnectAndReconnects() throws Exception {
        // Terminate current connection via an onComplete
        initialDelegate.lifecycle.onCompleted();

        // Verify that next retry succeeds
        verifySuccessAfterOneRetry();
    }

    @Test
    public void testDelegateChannelFailureAndReconnects() throws Exception {
        // Terminate current connection via an onError
        initialDelegate.lifecycle.onError(new Exception("msg"));

        // Verify that next retry succeeds
        verifySuccessAfterOneRetry();
    }

    @Test
    public void testDelegateChannelDisconnectLifecycleUnsubscribed() throws Exception {
        // Terminate current connection via an onError
        initialDelegate.lifecycle.onError(new Exception("msg"));

        // Verify that next retry succeeds
        verifySuccessAfterOneRetry();

        // Terminate current connection again
        initialDelegate.lifecycle.onCompleted();

        // Verify retryable channel has not changed state
        verifySuccessAfterOneRetry();
    }

    @Test
    public void testExponentialBackOffOnChannelDisconnect() throws Exception {
        verifyExponentialBackoffUpToMax(new Action0() {
            @Override
            public void call() {
                retryableChannel.currentDelegateChannel().lifecycle.onCompleted();
            }
        });
    }

    @Test
    public void testExponentialBackOffOnChannelError() throws Exception {
        verifyExponentialBackoffUpToMax(new Action0() {
            @Override
            public void call() {
                retryableChannel.currentDelegateChannel().lifecycle.onError(new Exception("msg"));
            }
        });
    }

    @Test
    public void testRecoversFromReestablishErrors() throws Exception {
        // Terminate current connection via an onError
        initialDelegate.lifecycle.onCompleted();

        doThrow(new RuntimeException("reestablish failed"))  // throw first time (without call)
                .doCallRealMethod()  // do the right thing after first throw
                .when(channelFactory).call();

        // Advance time by INITIAL_DELAY to trigger the reconnect
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);

        // reestablish fails, therefore initialDelegate is not closed, and is still the currentDelegate (id 0)
        verify(initialDelegate, times(0))._close();
        assertThat(retryableChannel.retryCount.get(), equalTo(1));
        assertThat(retryableChannel.reestablishAttemptCount.get(), equalTo(1));
        assertThat(retryableChannel.reestablishSuccessCount.get(), equalTo(0));
        assertThat(retryableChannel.currentDelegateChannel().myId, equalTo(0));

        // Advance time by 2*INITIAL_DELAY to trigger the second retry that succeeds
        scheduler.advanceTimeBy(2 * INITIAL_DELAY, TimeUnit.MILLISECONDS);

        verify(initialDelegate, times(1))._close();
        assertThat(retryableChannel.retryCount.get(), equalTo(2));
        assertThat(retryableChannel.reestablishAttemptCount.get(), equalTo(2));
        assertThat(retryableChannel.reestablishSuccessCount.get(), equalTo(1));
        assertThat(retryableChannel.currentDelegateChannel().myId, equalTo(1));
    }

    @Test
    public void testShutdownCleansUpResources() throws Exception {
        int originalRetryCounter = retryableChannel.retryCount.get();

        // Shutdown retryable channel (need to explicitly complete channel lifecycle).
        retryableChannel.close();

        verify(retryableChannel.currentDelegateChannel(), times(1)).close();

        // Advance time and verify that there are no more interactions
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);

        assertThat(retryableChannel.retryCount.get(), is(equalTo(originalRetryCounter)));
    }

    protected void verifySuccessAfterOneRetry() {
        // Advance time by INITIAL_DELAY to trigger the reconnect
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);

        verify(initialDelegate, times(1))._close();
        assertThat(retryableChannel.retryCount.get(), equalTo(1));
        assertThat(retryableChannel.reestablishAttemptCount.get(), equalTo(1));
        assertThat(retryableChannel.reestablishSuccessCount.get(), equalTo(1));
        assertThat(retryableChannel.currentDelegateChannel().myId, equalTo(1));
    }

    private void verifyExponentialBackoffUpToMax(Action0 channelFailureAction) {
        long delay = INITIAL_DELAY;
        int expectedNumberOfRetries = 0;
        for (int i = 0; i < 2 * RetryableServiceChannel.MAX_EXP_BACK_OFF_MULTIPLIER; i++) {
            channelFailureAction.call();
            scheduler.advanceTimeBy(delay, TimeUnit.MILLISECONDS);
            expectedNumberOfRetries++;  // delay++ after advance time
            delay = Math.min(MAX_DELAY, delay * 2);
        }

        // advance by max delay so channel delay resets
        scheduler.advanceTimeBy(MAX_DELAY, TimeUnit.MILLISECONDS);

        // verify that the reset worked
        channelFailureAction.call();
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);
        expectedNumberOfRetries++;

        verify(initialDelegate, times(1))._close();
        assertThat(retryableChannel.retryCount.get(), equalTo(expectedNumberOfRetries));
        assertThat(retryableChannel.reestablishAttemptCount.get(), equalTo(expectedNumberOfRetries));
        assertThat(retryableChannel.reestablishSuccessCount.get(), equalTo(expectedNumberOfRetries));
        assertThat(retryableChannel.currentDelegateChannel().myId, equalTo(expectedNumberOfRetries));
    }


    class RetryableTestChannel extends RetryableServiceChannel<TestChannel> {

        public final AtomicInteger retryCount = new AtomicInteger(0);
        public final AtomicInteger reestablishAttemptCount = new AtomicInteger(0);
        public final AtomicInteger reestablishSuccessCount = new AtomicInteger(0);

        private final Func0<TestChannel> channelFactory;

        protected RetryableTestChannel(Func0<TestChannel> channelFactory, long retryInitialDelayMs, Scheduler scheduler) {
            super(channelFactory.call(), retryInitialDelayMs, scheduler);
            this.channelFactory = channelFactory;
        }

        @Override
        protected Observable<TestChannel> reestablish() {
            reestablishAttemptCount.incrementAndGet();
            return Observable.create(new Observable.OnSubscribe<TestChannel>() {
                @Override
                public void call(Subscriber<? super TestChannel> subscriber) {
                    try {
                        final TestChannel newDelegate = channelFactory.call();
                        subscriber.onNext(newDelegate);
                        subscriber.onCompleted();
                        reestablishSuccessCount.incrementAndGet();
                    } catch (Exception e) {
                        subscriber.onError(e);
                    }
                }
            });
        }

        @Override
        protected void retry() {
            retryCount.incrementAndGet();
            super.retry();
        }
    }

    enum TestState {Ok}

    class TestChannel extends AbstractServiceChannel<TestState> {

        public final int myId;

        public TestChannel(int myId) {
            super(null, new NoOpStateMachineMetrics<TestState>());
            this.myId = myId;
        }

        @Override
        public void _close() {

        }
    }
}