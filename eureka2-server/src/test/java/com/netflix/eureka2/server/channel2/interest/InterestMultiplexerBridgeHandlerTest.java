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

package com.netflix.eureka2.server.channel2.interest;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.channel.interest.InterestMultiplexerBridgeHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Subscription;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 */
public class InterestMultiplexerBridgeHandlerTest {

    private final EurekaRegistryView<InstanceInfo> registry = mock(EurekaRegistryView.class);

    private final InterestMultiplexerBridgeHandler handler = new InterestMultiplexerBridgeHandler(registry);

    private final ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        ReplaySubject<ChangeNotification<InstanceInfo>> changeNotificationSubject = ReplaySubject.create();
        changeNotificationSubject.onNext(SampleChangeNotification.CliAdd.newNotification());

        when(registry.forInterest(any())).thenReturn(changeNotificationSubject);
    }

    @Test
    public void testInterestRegistration() throws Exception {
        PublishSubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = PublishSubject.create();
        Subscription subscription = handler.handle(interestNotifications).subscribe(testSubscriber);

        // First interest
        Interest<InstanceInfo> firstInterest = Interests.forFullRegistry();
        interestNotifications.onNext(ChannelNotification.newData(firstInterest));

        verify(registry, times(1)).forInterest(firstInterest);
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        // Second interest
        Interest<InstanceInfo> secondInterest = Interests.forApplications("testApp");
        interestNotifications.onNext(ChannelNotification.newData(secondInterest));

        verify(registry, times(1)).forInterest(secondInterest);
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));


        // Check that unsubscribe closes all subscriptions
        subscription.unsubscribe();
        assertThat(interestNotifications.hasObservers(), is(false));
    }

    @Test
    public void testInterestRegistrationOnCompleteClosesOutputSubscription() throws Exception {
        PublishSubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = PublishSubject.create();
        Subscription subscription = handler.handle(interestNotifications).subscribe(testSubscriber);

        assertThat(subscription.isUnsubscribed(), is(false));

        interestNotifications.onCompleted();
        assertThat(subscription.isUnsubscribed(), is(true));
    }
}