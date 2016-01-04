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

package com.netflix.eureka2.client.registration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaRegistrationClient.RegistrationStatus;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
public class EurekaRegistrationClientImplTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.Backend.build();

    private static final long RETRY_DELAY_MS = 1000;

    private final Source serverSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testServer");

    private final TestScheduler testScheduler = Schedulers.test();

    private final ServerResolver serverResolver = mock(ServerResolver.class);

    private final EurekaClientTransportFactory transportFactory = mock(EurekaClientTransportFactory.class);
    private final EurekaTransportConfig transportConfig = mock(EurekaTransportConfig.class);
    private final RegistrationHandlerStub transportHandler = new RegistrationHandlerStub();

    private EurekaRegistrationClientImpl client;

    @Before
    public void setUp() throws Exception {
        // Server resolver
        when(serverResolver.resolve()).thenReturn(Observable.just(new Server("testHost", 123)));

        // Transport
        when(transportConfig.getHeartbeatIntervalMs()).thenReturn(30000L);
        when(transportFactory.newRegistrationClientTransport(any())).thenReturn(transportHandler);

        Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testClient");
        client = new EurekaRegistrationClientImpl(clientSource, serverResolver, transportFactory, transportConfig, RETRY_DELAY_MS, testScheduler);
    }

    @Test
    public void testRegistrationLifecycle() throws Exception {
        ExtTestSubscriber<RegistrationStatus> testSubscriber = new ExtTestSubscriber<>();
        ReplaySubject<InstanceInfo> registrationUpdates = ReplaySubject.create();
        Subscription subscription = client.register(registrationUpdates).subscribe(testSubscriber);

        // Registration
        registrationUpdates.onNext(INSTANCE);
        testScheduler.triggerActions();

        assertThat(transportHandler.getPipelineCount(), is(equalTo(1)));
        assertThat(testSubscriber.takeNext(), is(equalTo(RegistrationStatus.Registered)));

        // Unsubscribe
        subscription.unsubscribe();
        testScheduler.triggerActions();
        assertThat(registrationUpdates.hasObservers(), is(false));
    }

    @Test
    public void testRegistrationSubscriptionFailover() throws Exception {
        ExtTestSubscriber<RegistrationStatus> testSubscriber = new ExtTestSubscriber<>();
        ReplaySubject<InstanceInfo> registrationUpdates = ReplaySubject.create();
        client.register(registrationUpdates).subscribe(testSubscriber);

        // First registration
        registrationUpdates.onNext(INSTANCE);
        testScheduler.triggerActions();

        assertThat(transportHandler.getPipelineCount(), is(equalTo(1)));
        assertThat(testSubscriber.takeNext(), is(equalTo(RegistrationStatus.Registered)));

        // Simulate error
        transportHandler.disconnect();
        testScheduler.advanceTimeBy(RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        assertThat(transportHandler.getPipelineCount(), is(equalTo(2)));
    }

    private class RegistrationHandlerStub implements RegistrationHandler {
        private volatile PublishSubject<ChannelNotification<InstanceInfo>> replySubject;
        private volatile int pipelineCount;

        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
            return Observable.create(subscriber -> {
                pipelineCount++;

                this.replySubject = PublishSubject.create();
                replySubject.subscribe(subscriber);

                Subscription subscription = registrationUpdates.subscribe(
                        inputNotification -> {
                            if (inputNotification.getKind() == ChannelNotification.Kind.Hello) {
                                replySubject.onNext(ChannelNotification.newHello(serverSource));
                            } else if (inputNotification.getKind() == ChannelNotification.Kind.Heartbeat) {
                                replySubject.onNext(ChannelNotification.newHeartbeat());
                            } else {
                                replySubject.onNext(ChannelNotification.newData(inputNotification.getData()));
                            }
                        },
                        e -> {
                            replySubject.onError(e);
                            e.printStackTrace();
                        },
                        () -> {
                            System.out.println("Transport reply subscription terminated");
                            replySubject.onCompleted();
                        });

                subscriber.add(subscription);
            });
        }

        void disconnect() {
            replySubject.onError(new IOException("Simulated transport error"));
        }

        int getPipelineCount() {
            return pipelineCount;
        }
    }
}