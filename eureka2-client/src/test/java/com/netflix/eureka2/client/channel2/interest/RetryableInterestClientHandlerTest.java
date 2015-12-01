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
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.client.channel2.ChannelTestkit.CHANNEL_INTEREST_NOTIFICATION_STREAM;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
public class RetryableInterestClientHandlerTest {

    private static final ChangeNotification<InstanceInfo> CHANGE_NOTIFICATION = new ChangeNotification<>(ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build());
    private static final ChannelNotification<ChangeNotification<InstanceInfo>> CHANNEL_NOTIFICATION = ChannelNotification.newData(CHANGE_NOTIFICATION);

    private static final long RETRY_DELAY_MS = 5 * 1000;

    private final TestScheduler testScheduler = Schedulers.test();
    private final ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();

    private final ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> factory = mock(ChannelPipelineFactory.class);
    private final InterestHandler nextHandler = mock(InterestHandler.class);

    private final RetryableInterestClientHandler handler = new RetryableInterestClientHandler(
            factory,
            RETRY_DELAY_MS,
            testScheduler
    );

    @Before
    public void setUp() throws Exception {
        Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> pipelineObservable = Observable.just(
                new ChannelPipeline<>("interest", nextHandler)
        );
        when(factory.createPipeline()).thenReturn(pipelineObservable);
    }

    @Test
    public void testRetryOnFailure() throws Exception {
        PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replySubject = PublishSubject.create();
        when(nextHandler.handle(any())).thenReturn(replySubject);

        handler.handle(CHANNEL_INTEREST_NOTIFICATION_STREAM).subscribe(testSubscriber);
        testScheduler.triggerActions();

        // First uninterrupted notification
        replySubject.onNext(CHANNEL_NOTIFICATION);
        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        // Now reply with onError, and check reconnect
        PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replySubject2 = PublishSubject.create();
        when(nextHandler.handle(any())).thenReturn(replySubject2);

        replySubject.onError(new IOException("Simulated channel error"));
        testScheduler.advanceTimeBy(2 * RETRY_DELAY_MS, TimeUnit.MILLISECONDS);

        replySubject2.onNext(CHANNEL_NOTIFICATION);
        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNextOrWait().getKind(), is(equalTo(ChannelNotification.Kind.Data)));
    }
}
