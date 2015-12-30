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

package com.netflix.eureka2.client.channel2.register;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.*;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.channel.ChannelTestkit.CHANNEL_INSTANCE_NOTIFICATION;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
public class RetryableRegistrationClientHandlerTest {

    private static final long RETRY_DELAY_MS = 5 * 1000;

    private final TestScheduler testScheduler = Schedulers.test();

    private final ChannelPipelineFactory<InstanceInfo, InstanceInfo> factory = mock(ChannelPipelineFactory.class);
    private final RegistrationHandlerStub nextHandler = new RegistrationHandlerStub();

    private final RetryableRegistrationClientHandler handler = new RetryableRegistrationClientHandler(
            factory,
            RETRY_DELAY_MS,
            testScheduler
    );

    private final ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        Observable<ChannelPipeline<InstanceInfo, InstanceInfo>> pipelineObservable = Observable.just(
                new ChannelPipeline<>("registration", nextHandler)
        );
        when(factory.createPipeline()).thenReturn(pipelineObservable);
    }

    @Test
    public void testRetryOnFailure() throws Exception {
        ReplaySubject<ChannelNotification<InstanceInfo>> registrationUpdates = ReplaySubject.create();
        handler.handle(registrationUpdates).subscribe(testSubscriber);

        // First uninterrupted notification
        registrationUpdates.onNext(CHANNEL_INSTANCE_NOTIFICATION);
        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        // Now reply with onError, and check reconnect
        nextHandler.emitOnError();
        testScheduler.advanceTimeBy(2 * RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        assertThat(testSubscriber.takeNextOrWait().getKind(), is(equalTo(ChannelNotification.Kind.Data)));
    }

    static class RegistrationHandlerStub implements RegistrationHandler {

        private volatile Subject<ChannelNotification<InstanceInfo>, ChannelNotification<InstanceInfo>> replySubject;

        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
            return Observable.create(subscriber -> {
                replySubject = new SerializedSubject<>(PublishSubject.create());
                replySubject.subscribe(subscriber);

                registrationUpdates.subscribe(
                        next -> replySubject.onNext(next)
                );
            });
        }

        void emitOnError() {
            replySubject.onError(new IOException("Simulated channel error"));
        }
    }
}