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

package com.netflix.eureka2.server.channel2;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class ServerHeartbeatHandlerTest {

    private static final long HEARTBEAT_TIMEOUT = 3 * 30 * 1000;

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.Backend.build();

    private final TestScheduler testScheduler = Schedulers.test();

    private final ChannelHandler<InstanceInfo, InstanceInfo> nextHandler = new ChannelHandlerStub();

    private final PublishSubject<ChannelNotification<InstanceInfo>> inputStream = PublishSubject.create();
    private final ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    private final ServerHeartbeatHandler<InstanceInfo, InstanceInfo> handler = new ServerHeartbeatHandler<>(HEARTBEAT_TIMEOUT, testScheduler);
    private Subscription subscription;

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("heartbeat", handler, nextHandler);
        subscription = handler.handle(inputStream).subscribe(testSubscriber);
    }

    @Test
    public void testHeartbeat() throws Exception {
        // Check heartbeat
        inputStream.onNext(ChannelNotification.newHeartbeat());
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Heartbeat)));

        // CHeck data is passed transparently
        inputStream.onNext(ChannelNotification.newData(INSTANCE));
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        // Check that unsubscribe closes all subscriptions
        subscription.unsubscribe();
        assertThat(inputStream.hasObservers(), is(false));
    }

    @Test
    public void testTimeout() throws Exception {
        // Send heartbeat
        inputStream.onNext(ChannelNotification.newHeartbeat());
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Heartbeat)));

        // Wait until timeout
        testScheduler.advanceTimeBy(HEARTBEAT_TIMEOUT, TimeUnit.MILLISECONDS);
        testSubscriber.assertOnError();

        // Check that unsubscribe closes all subscriptions
        subscription.unsubscribe();
        assertThat(inputStream.hasObservers(), is(false));
    }

    static class ChannelHandlerStub implements ChannelHandler<InstanceInfo, InstanceInfo> {

        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> inputStream) {
            return inputStream;
        }
    }
}