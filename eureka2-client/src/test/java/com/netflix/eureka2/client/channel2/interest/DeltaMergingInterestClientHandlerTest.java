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

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka2.client.channel2.ChannelTestkit.INTEREST;
import static com.netflix.eureka2.client.channel2.ChannelTestkit.unbound;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
public class DeltaMergingInterestClientHandlerTest {

    private final DeltaMergingInterestClientHandler handler = new DeltaMergingInterestClientHandler();

    private final InterestHandler nextHandler = mock(InterestHandler.class);

    private final ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("interest", handler, nextHandler);
    }

    @Test
    public void testAddIsForwarded() throws Exception {
        Observable<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = unbound(INTEREST);
        ChangeNotification<InstanceInfo> notification = new ChangeNotification<>(ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build());

        when(nextHandler.handle(interestNotifications)).thenReturn(unbound(notification));

        handler.handle(interestNotifications).subscribe(testSubscriber);

        ChannelNotification<ChangeNotification<InstanceInfo>> actual = testSubscriber.takeNext();
        assertThat(actual.getData(), is(equalTo(notification)));
    }

    @Test
    @Ignore
    public void testMergeIsConvertedToAdd() throws Exception {
        // Implement delta for Grpc
    }
}