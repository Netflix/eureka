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

package com.netflix.rx.eureka.transport.base;

import com.netflix.rx.eureka.protocol.Heartbeat;
import com.netflix.rx.eureka.transport.MessageConnection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class HeartBeatConnectionTest {

    private static final String MESSAGE = "My MESSAGE";

    private static final long HEART_BEAT_INTERVAL = 5000;
    private static final long TOLERANCE = 3;

    @Mock
    private MessageConnection delegate;

    private final TestScheduler scheduler = Schedulers.test();

    private HeartBeatConnection heartBeatConnection;
    private final PublishSubject<Object> mockedIncomingStream = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        when(delegate.incoming()).thenReturn(mockedIncomingStream);
        heartBeatConnection = new HeartBeatConnection(delegate, HEART_BEAT_INTERVAL, TOLERANCE, scheduler);

        when(delegate.submit(Heartbeat.INSTANCE)).thenReturn(Observable.<Void>empty());
    }

    @Test
    public void testHeartbeatIsSentPeriodically() throws Exception {
        // Just after first heartbeat should be sent
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);

        verify(delegate, times(1)).submit(Heartbeat.INSTANCE);
    }

    @Test
    public void testConnectionIsClosedIfHeartbeatSentFailed() throws Exception {
        // Advance time to cross tolerance level
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL * (TOLERANCE + 1), TimeUnit.MILLISECONDS);

        verify(delegate, times(1)).shutdown();
    }

    @Test
    public void testHeartbeatIsReceivedAndCounted() throws Exception {
        // Sent heartbeat
        mockedIncomingStream.onNext(Heartbeat.INSTANCE);
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);

        // Still within the limits
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL * TOLERANCE, TimeUnit.MILLISECONDS);

        verify(delegate, times(0)).shutdown();

        // Now cross the limit
        scheduler.advanceTimeBy(HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);
        verify(delegate, times(1)).shutdown();
    }

    @Test
    public void testNonHeartbeatInputMessagesArePassedThrough() throws Exception {
        Iterator<String> input = heartBeatConnection.incoming().cast(String.class).toBlocking().getIterator();
        mockedIncomingStream.onNext(MESSAGE);
        mockedIncomingStream.onCompleted();
        assertThat(input.next(), is(equalTo(MESSAGE)));
        assertThat(input.hasNext(), is(false));
    }
}