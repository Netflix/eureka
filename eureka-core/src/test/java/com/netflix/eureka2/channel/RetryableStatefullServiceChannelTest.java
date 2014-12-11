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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.channel.RetryableStatefullServiceChannel.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class RetryableStatefullServiceChannelTest {

    private static final long INITIAL_DELAY = 1000;
    private static final long MAX_DELAY = MAX_EXP_BACK_OFF_MULTIPLIER * INITIAL_DELAY;

    public static final int STATE_OK = 1;
    public static final int STATE_OK2 = 3;
    public static final int STATE_WHEN_FAILURE = 2;

    private final TestScheduler scheduler = Schedulers.test();
    private final ServiceChannel channel = mock(ServiceChannel.class);

    private SampleRetryableChannelConsumer consumer;
    private ReplaySubject<Void> lifecyclePublisher;


    @Before
    public void setUp() throws Exception {
        withNewLifecyclePublisher();
        consumer = new SampleRetryableChannelConsumer(INITIAL_DELAY, scheduler);
    }

    @After
    public void tearDown() throws Exception {
        consumer.close();
    }

    @Test
    public void testObserversChannelDisconnectAndReconnects() throws Exception {
        // Terminate current connection
        lifecyclePublisher.onCompleted();

        // Verify that next retry succeeds
        reestablishOnNextRetry(INITIAL_DELAY, STATE_OK);
    }

    @Test
    public void testObserversChannelFailureAndReconnects() throws Exception {
        // Terminate current connection
        lifecyclePublisher.onError(new Exception("channel error"));

        // Verify that next retry succeeds
        reestablishOnNextRetry(INITIAL_DELAY, STATE_OK);
    }

    @Test
    public void testExponentialBackOffOnChannelDisconnect() throws Exception {
        // Terminate current connection
        lifecyclePublisher.onCompleted();

        // Prepare new state
        consumer.withReestablish(STATE_WHEN_FAILURE);
        consumer.withRepopulate(Observable.<Void>empty());

        // Verify that next retry delay increases up to the max value
        reestablishAfterExponentialBackOff();
    }

    @Test
    public void testExponentialBackOffOnChannelError() throws Exception {
        // Terminate current connection
        lifecyclePublisher.onError(new Exception("channel error"));

        // Prepare new state
        consumer.withReestablish(STATE_WHEN_FAILURE);
        consumer.withRepopulate(Observable.<Void>empty());

        // Verify that next retry delay increases up to the max value
        reestablishAfterExponentialBackOff();
    }

    @Test
    public void testReturnsToInitialDelayForStableConnection() throws Exception {
        // Terminate current connection
        lifecyclePublisher.onCompleted();

        // Prepare new state
        consumer.withReestablish(STATE_WHEN_FAILURE);
        consumer.withRepopulate(Observable.<Void>empty());

        // Verify that next retry delay increases up to the max value
        reestablishAfterExponentialBackOff();

        // Make the time pass by MAX_DELAY, to match connection stability criteria
        scheduler.advanceTimeBy(MAX_DELAY, TimeUnit.MILLISECONDS);

        // Terminate current connection
        lifecyclePublisher.onCompleted();

        // Verify that next retry succeeds
        reestablishOnNextRetry(INITIAL_DELAY, STATE_OK2);
    }

    @Test
    public void testRecoversFromRepopulateErrors() throws Exception {
        // Terminate current connection
        lifecyclePublisher.onCompleted();

        // Fail repopulate action
        consumer.withReestablish(STATE_WHEN_FAILURE);
        consumer.withRepopulate(Observable.<Void>error(new Exception("Repopulate error")));
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);

        assertThat(consumer.getStateWithChannel().getState(), not(equalTo(STATE_WHEN_FAILURE)));

        // Verify that next retry succeeds
        reestablishOnNextRetry(2 * INITIAL_DELAY, STATE_OK);
    }

    @Test
    public void testShutdownCleansUpResources() throws Exception {
        long originalRetryCounter = consumer.retryCounter;

        // Shutdown retryable channel (need to explicitly complete channel lifecycle).
        consumer.close();
        lifecyclePublisher.onCompleted();

        verify(channel, times(1)).close();

        // Advance time and verify that there are no more interactions
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);

        assertThat(consumer.retryCounter, is(equalTo(originalRetryCounter)));
    }

    protected void withNewLifecyclePublisher() {
        lifecyclePublisher = ReplaySubject.create();
        when(channel.asLifecycleObservable()).thenReturn(lifecyclePublisher);
    }

    protected void reestablishOnNextRetry(long delay, int state) {
        // Prepare new state
        consumer.withReestablish(state);
        consumer.withRepopulate(Observable.<Void>empty());
        withNewLifecyclePublisher();

        // Advance time by INITIAL_DELAY to trigger the reconnect
        scheduler.advanceTimeBy(delay, TimeUnit.MILLISECONDS);

        assertThat(consumer.getStateWithChannel().getState(), is(equalTo(state)));
    }

    private void reestablishAfterExponentialBackOff() {
        long delay = INITIAL_DELAY;
        long expectedNumberOfRetries = 1; // Original
        for (int i = 0; i < 2 * MAX_EXP_BACK_OFF_MULTIPLIER; i++) {
            scheduler.advanceTimeBy(delay, TimeUnit.MILLISECONDS);
            expectedNumberOfRetries++;
            delay = Math.min(MAX_DELAY, delay * 2);
        }

        // Now lets make it succeed
        reestablishOnNextRetry(MAX_DELAY, STATE_OK);
        expectedNumberOfRetries++;

        assertThat(consumer.retryCounter, is(equalTo(expectedNumberOfRetries)));
    }

    class SampleRetryableChannelConsumer extends RetryableStatefullServiceChannel<ServiceChannel, Integer> {

        private int stateValue;
        private Observable<Void> repopulateReply;
        public long retryCounter;

        protected SampleRetryableChannelConsumer(long retryInitialDelayMs, Scheduler scheduler) {
            super(retryInitialDelayMs, scheduler);
            initializeRetryableChannel();
        }

        @Override
        protected StateWithChannel reestablish() {
            retryCounter++;
            return new StateWithChannel(channel, stateValue);
        }

        @Override
        protected Observable<Void> repopulate(StateWithChannel newState) {
            return repopulateReply;
        }

        @Override
        protected void release(StateWithChannel oldState) {
        }

        public void withReestablish(int stateValue) {
            this.stateValue = stateValue;
        }

        public void withRepopulate(Observable<Void> repopulateReply) {
            this.repopulateReply = repopulateReply;
        }

        @Override
        public Observable<Void> asLifecycleObservable() {
            return null;
        }
    }
}