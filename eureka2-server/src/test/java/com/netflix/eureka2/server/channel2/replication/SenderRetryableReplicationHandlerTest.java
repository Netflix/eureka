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

package com.netflix.eureka2.server.channel2.replication;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
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

import static com.netflix.eureka2.client.channel2.ChannelTestkit.unbound;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
public class SenderRetryableReplicationHandlerTest {

    private static final ChangeNotification<InstanceInfo> CHANGE_NOTIFICATION = new ChangeNotification<>(ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build());

    private static final long RETRY_DELAY_MS = 5 * 1000;

    private final TestScheduler testScheduler = Schedulers.test();
    private final ExtTestSubscriber<ChannelNotification<Void>> testSubscriber = new ExtTestSubscriber<>();

    private final ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> factory = mock(ChannelPipelineFactory.class);
    private final ReplicationHandler nextHandler = mock(ReplicationHandler.class);

    private final SenderRetryableReplicationHandler handler = new SenderRetryableReplicationHandler(
            factory,
            RETRY_DELAY_MS,
            testScheduler
    );

    @Before
    public void setUp() throws Exception {
        Observable<ChannelPipeline<ChangeNotification<InstanceInfo>, Void>> pipelineObservable = Observable.just(
                new ChannelPipeline<>("replication", nextHandler)
        );
        when(factory.createPipeline()).thenReturn(pipelineObservable);
    }

    @Test
    public void testRetryOnFailure() throws Exception {
        PublishSubject<ChannelNotification<Void>> replySubject = PublishSubject.create();
        when(nextHandler.handle(any())).thenReturn(replySubject);

        handler.handle(unbound(CHANGE_NOTIFICATION)).subscribe(testSubscriber);
        testScheduler.triggerActions();

        // Reply with onError, and check reconnect
        PublishSubject<ChannelNotification<Void>> replySubject2 = PublishSubject.create();
        when(nextHandler.handle(any())).thenReturn(replySubject2);

        replySubject.onError(new IOException("Simulated channel error"));
        testScheduler.advanceTimeBy(2 * RETRY_DELAY_MS, TimeUnit.MILLISECONDS);

        assertThat(replySubject2.hasObservers(), is(true));
    }

    @Test
    public void testLoopErrorsAreNotRetried() throws Exception {
        PublishSubject<ChannelNotification<Void>> replySubject = PublishSubject.create();
        when(nextHandler.handle(any())).thenReturn(replySubject);

        handler.handle(unbound(CHANGE_NOTIFICATION)).subscribe(testSubscriber);
        testScheduler.triggerActions();

        // Reply with onError, and check reconnect
        PublishSubject<ChannelNotification<Void>> replySubject2 = PublishSubject.create();
        when(nextHandler.handle(any())).thenReturn(replySubject2);

        replySubject.onError(ReplicationLoopException.INSTANCE);
        testScheduler.advanceTimeBy(2 * RETRY_DELAY_MS, TimeUnit.MILLISECONDS);

        assertThat(replySubject2.hasObservers(), is(false));
    }
}