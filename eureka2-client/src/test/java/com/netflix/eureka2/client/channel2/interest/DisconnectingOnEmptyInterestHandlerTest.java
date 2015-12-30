/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.client.channel2.interest;

import com.netflix.eureka2.client.channel.interest.DisconnectingOnEmptyInterestHandler;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 */
public class DisconnectingOnEmptyInterestHandlerTest {

    private final DisconnectingOnEmptyInterestHandler handler = new DisconnectingOnEmptyInterestHandler();

    private final InterestHandler nextHandler = mock(InterestHandler.class);

    private final ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("interest", handler, nextHandler);
    }

    @Test
    public void testNonEmptyInterestUpdatesKeepPipeline() throws Exception {
        ChangeNotification<InstanceInfo> changeNotification = new ChangeNotification<>(ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build());
        ChannelNotification<ChangeNotification<InstanceInfo>> channelNotification = ChannelNotification.newData(changeNotification);

        PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> notificationSubject = PublishSubject.create();
        when(nextHandler.handle(any())).thenReturn(notificationSubject);

        PublishSubject<ChannelNotification<Interest<InstanceInfo>>> interestUpdates = PublishSubject.create();
        handler.handle(interestUpdates).subscribe(testSubscriber);

        // First non-empty interest update
        interestUpdates.onNext(ChannelNotification.newData(SampleInterest.DiscoveryApp.build()));
        notificationSubject.onNext(channelNotification);
        assertThat(testSubscriber.takeNext().getData(), is(equalTo(changeNotification)));
        verify(nextHandler, times(1)).handle(any());

        // Second non-empty interest update
        interestUpdates.onNext(ChannelNotification.newData(SampleInterest.DiscoveryVip.build()));
        notificationSubject.onNext(channelNotification);
        assertThat(testSubscriber.takeNext().getData(), is(equalTo(changeNotification)));
        verify(nextHandler, times(1)).handle(any());

        // Now empty subscription
        assertThat(notificationSubject.hasObservers(), is(true));
        interestUpdates.onNext(ChannelNotification.newData(Interests.forNone()));
        assertThat(notificationSubject.hasObservers(), is(false));

        // Another non-empty interest creates a new underlying pipeline subscription
        interestUpdates.onNext(ChannelNotification.newData(SampleInterest.DiscoveryVip.build()));
        notificationSubject.onNext(channelNotification);
        assertThat(testSubscriber.takeNext().getData(), is(equalTo(changeNotification)));
        verify(nextHandler, times(2)).handle(any());
    }
}