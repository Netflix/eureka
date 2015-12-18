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

package com.netflix.eureka2.client.channel2;

import com.netflix.eureka2.channel2.client.ClientHeartbeatHandler;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.client.channel2.ChannelTestkit.CHANNEL_INSTANCE_NOTIFICATION;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class ClientHeartbeatHandlerTest {

    private static final long HEARTBEAT_INTERVAL_MS = 30 * 1000;

    private final TestScheduler testScheduler = Schedulers.test();
    private final ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    private final ClientHeartbeatHandler<InstanceInfo, InstanceInfo> handler = new ClientHeartbeatHandler<>(HEARTBEAT_INTERVAL_MS, testScheduler);
    private final ChannelHandlerStub nextHandler = new ChannelHandlerStub();

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("heartbeat", handler, nextHandler);
    }

    @Test
    public void testHeartbeatInjection() throws Exception {
        PublishSubject<ChannelNotification<InstanceInfo>> inputSubject = PublishSubject.create();
        handler.handle(inputSubject).subscribe(testSubscriber);

        // Verify first that heartbeats are periodically sent
        testHeartbeatCycle(5);

        // Check that regular input notifications are propagated
        inputSubject.onNext(CHANNEL_INSTANCE_NOTIFICATION);
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        // Verify first that heartbeats are periodically sent
        testHeartbeatCycle(5);
    }

    @Test
    public void testHeartbeatTimeout() throws Exception {
        PublishSubject<ChannelNotification<InstanceInfo>> inputSubject = PublishSubject.create();
        handler.handle(inputSubject).subscribe(testSubscriber);

        // Verify first that heartbeats are periodically sent
        testHeartbeatCycle(5);

        // Now block heartbeat replies, and wait until timeout occurs
        nextHandler.blockHeartbeats();
        testScheduler.advanceTimeBy(10 * HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        testSubscriber.assertOnError();
    }

    private void testHeartbeatCycle(int count) {
        int initial = nextHandler.getHeartbeatCounter();
        for (int i = 1; i <= count; i++) {
            testScheduler.advanceTimeBy(HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
            assertThat(nextHandler.getHeartbeatCounter() - initial, is(equalTo(i)));
        }
    }

    static class ChannelHandlerStub implements ChannelHandler<InstanceInfo, InstanceInfo> {

        private volatile int heartbeatCounter;
        private volatile boolean heartbeatsBlocked;

        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> inputStream) {
            return inputStream
                    .doOnNext(inputNotification -> {
                        if (inputNotification.getKind() == ChannelNotification.Kind.Heartbeat) {
                            heartbeatCounter++;
                        }
                    })
                    .filter(inputNotification -> inputNotification.getKind() != ChannelNotification.Kind.Heartbeat || !heartbeatsBlocked);
        }

        int getHeartbeatCounter() {
            return heartbeatCounter;
        }

        public void blockHeartbeats() {
            heartbeatsBlocked = true;
        }
    }
}