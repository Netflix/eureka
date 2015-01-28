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

package com.netflix.eureka2.client.channel;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func0;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class RetryableRegistrationChannelTest {

    private static final long INITIAL_DELAY = 1000;
    private static final InstanceInfo INSTANCE_INFO = SampleInstanceInfo.DiscoveryServer.build();

    private final TestScheduler scheduler = Schedulers.test();

    private final RegistrationChannel delegateChannel1 = mock(RegistrationChannel.class);
    private final ReplaySubject<Void> channelLifecycle1 = ReplaySubject.create();

    private final RegistrationChannel delegateChannel2 = mock(RegistrationChannel.class);
    private final ReplaySubject<Void> channelLifecycle2 = ReplaySubject.create();

    private final Func0<RegistrationChannel> channelFactory = new Func0<RegistrationChannel>() {

        private final RegistrationChannel[] channels = {delegateChannel1, delegateChannel2};
        private int idx;

        @Override
        public RegistrationChannel call() {
            RegistrationChannel next = channels[idx];
            idx = (idx + 1) % channels.length;
            return next;
        }
    };

    private RetryableRegistrationChannel channel;

    @Before
    public void setUp() throws Exception {
        withChannelMocks(delegateChannel1, channelLifecycle1);
        withChannelMocks(delegateChannel2, channelLifecycle2);
        channel = spy(new RetryableRegistrationChannel(channelFactory, INITIAL_DELAY, scheduler));
    }

    @After
    public void tearDown() throws Exception {
        channel.close();
    }

    @Test(timeout = 60000)
    public void testForwardsRequestsToDelegate() throws Exception {
        channel.register(INSTANCE_INFO).subscribe();
        verify(delegateChannel1, timeout(1)).register(INSTANCE_INFO);

        InstanceInfo newInfo = new InstanceInfo.Builder().withInstanceInfo(INSTANCE_INFO).withVipAddress("aNewName").build();
        channel.register(newInfo).subscribe();
        verify(delegateChannel1, timeout(1)).register(newInfo);

        channel.unregister().subscribe();
        verify(delegateChannel1, timeout(1)).unregister();
    }

    @Test(timeout = 60000)
    public void testReconnectsWhenChannelFailure() throws Exception {
        // First channel registration
        channel.register(INSTANCE_INFO).subscribe();
        verify(delegateChannel1, timeout(1)).register(INSTANCE_INFO);

        // Break the channel
        channelLifecycle1.onError(new Exception("channel error"));

        // Verify that reconnected
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);
        verify(delegateChannel2, times(1)).register(INSTANCE_INFO);
        verify(delegateChannel1, times(1)).register(INSTANCE_INFO);  // make sure delegate1 is not called again

        // Verify that new requests relayed to the new channel
        channel.unregister().subscribe();
        verify(delegateChannel2, times(1)).unregister();
    }

    @Test(timeout = 60000)
    public void testReconnectAfterUnregisterDoesNotActivelyReregister() throws Exception {
        // First channel registration
        channel.register(INSTANCE_INFO).subscribe();
        verify(delegateChannel1, timeout(1)).register(INSTANCE_INFO);

        channel.unregister().subscribe();
        verify(delegateChannel1, timeout(1)).unregister();

        // Break the channel
        channelLifecycle1.onError(new Exception("channel error"));

        // Verify that reconnected
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);

        verify(delegateChannel2, times(0)).register(INSTANCE_INFO);

        // Verify that new requests relayed to the new channel
        channel.register(INSTANCE_INFO).subscribe();
        verify(delegateChannel2, timeout(1)).register(INSTANCE_INFO);
    }

    @Test(timeout = 60000)
    public void testClosesInternalChannels() throws Exception {
        // First channel registration
        channel.register(INSTANCE_INFO).subscribe();

        // Break the channel and reconnect
        channelLifecycle1.onError(new Exception("channel error"));
        scheduler.advanceTimeBy(INITIAL_DELAY, TimeUnit.MILLISECONDS);
        verify(delegateChannel1, times(1)).close();  // verify first channel is closed

        channel.close();
        verify(delegateChannel2, times(1)).close();  // verify second channel is closed
    }

    protected void withChannelMocks(RegistrationChannel channel, Observable<Void> channelLifecycle) {
        when(channel.register(any(InstanceInfo.class))).thenReturn(Observable.<Void>empty());
        when(channel.unregister()).thenReturn(Observable.<Void>empty());
        when(channel.asLifecycleObservable()).thenReturn(channelLifecycle);
    }
}