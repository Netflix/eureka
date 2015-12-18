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

package com.netflix.eureka2.transport.base;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class HeartBeatConnectionTest {

    private static final String MESSAGE = "My MESSAGE";

    private static final long HEART_BEAT_INTERVAL = 5000;
    private static final long TOLERANCE = 3;

    @Mock
    private EurekaConnection delegate;

    private final ReplaySubject<Void> delegateLifecycleSubject = ReplaySubject.create();

    private final TestScheduler scheduler = Schedulers.test();

    private HeartBeatConnection heartBeatConnection;
    private final PublishSubject<Object> mockedIncomingStream = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        when(delegate.incoming()).thenReturn(mockedIncomingStream);
        when(delegate.lifecycleObservable()).thenReturn(delegateLifecycleSubject);

        heartBeatConnection = new HeartBeatConnection(delegate, HEART_BEAT_INTERVAL, TOLERANCE, scheduler);

        when(delegate.submit(ProtocolModel.getDefaultModel().newHeartbeat())).thenReturn(Observable.<Void>empty());
    }

    @Test(timeout = 60000)
    public void testHeartbeatIsSentPeriodically() throws Exception {
        // Just after first heartbeat should be sent
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);

        verify(delegate, times(1)).submit(ProtocolModel.getDefaultModel().newHeartbeat());
    }

    @Test(timeout = 60000)
    public void testConnectionIsClosedIfHeartbeatSentFailed() throws Exception {
        // Advance time to cross tolerance level
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL * (TOLERANCE + 1), TimeUnit.MILLISECONDS);

        verify(delegate, times(1)).shutdown(org.mockito.Matchers.any(Exception.class));
    }

    @Test(timeout = 60000)
    public void testHeartbeatIsReceivedAndCounted() throws Exception {
        // Sent heartbeat
        mockedIncomingStream.onNext(ProtocolModel.getDefaultModel().newHeartbeat());
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);

        // Still within the limits
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL * TOLERANCE, TimeUnit.MILLISECONDS);

        verify(delegate, times(0)).shutdown();

        // Now cross the limit
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);
        verify(delegate, times(1)).shutdown(HeartBeatConnection.MISSING_HEARTBEAT_EXCEPTION);
    }

    @Test(timeout = 60000)
    public void testNonHeartbeatInputMessagesArePassedThrough() throws Exception {
        Iterator<String> input = heartBeatConnection.incoming().cast(String.class).toBlocking().getIterator();
        mockedIncomingStream.onNext(MESSAGE);
        mockedIncomingStream.onCompleted();
        assertThat(input.next(), is(equalTo(MESSAGE)));
        assertThat(input.hasNext(), is(false));
    }

    @Test
    public void testHeartbeatsAreStoppedIfLifecycleStateChangesToClosed() throws Exception {
        delegateLifecycleSubject.onCompleted(); // Simulate connection disconnect

        // Advance time to cross tolerance level
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL * (TOLERANCE + 1), TimeUnit.MILLISECONDS);

        // Check that no heartbeat message was sent
        verify(delegate, times(0)).submit(ProtocolModel.getDefaultModel().newHeartbeat());
    }
}