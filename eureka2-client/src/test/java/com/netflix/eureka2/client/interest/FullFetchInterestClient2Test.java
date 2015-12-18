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

package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.ext.grpc.model.GrpcModelsInjector;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotification;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIXME This test code is identical to EurekaInterestClientImpl2Test. Move FullFetchInterestClient2 and EurekaInterestClientImpl2 to common package and reuse test code
 */
public class FullFetchInterestClient2Test {

    static {
        GrpcModelsInjector.injectGrpcModels();
    }

    private static final long RETRY_DELAY_MS = 1000;

    private final Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testClient");
    private final Source serverSource = InstanceModel.getDefaultModel().createSource(Source.Origin.INTERESTED, "testServer");

    private final TestScheduler testScheduler = Schedulers.test();

    private final ServerResolver serverResolver = mock(ServerResolver.class);

    private final EurekaRegistry<InstanceInfo> eurekaRegistry = mock(EurekaRegistry.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> registrySubject = PublishSubject.create();

    private final EurekaClientTransportFactory transportFactory = mock(EurekaClientTransportFactory.class);
    private final EurekaTransportConfig transportConfig = mock(EurekaTransportConfig.class);
    private final InterestHandlerStub transportHandler = new InterestHandlerStub();

    private FullFetchInterestClient2 client;

    @Before
    public void setUp() throws Exception {
        // Server resolver
        when(serverResolver.resolve()).thenReturn(Observable.just(new Server("testHost", 123)));

        // Transport
        when(transportConfig.getHeartbeatIntervalMs()).thenReturn(30000L);
        when(transportFactory.newInterestTransport(any())).thenReturn(transportHandler);

        // Eureka registry
        when(eurekaRegistry.forInterest(any())).thenReturn(registrySubject);
        when(eurekaRegistry.connect(any(), any())).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                Source source = (Source) invocation.getArguments()[0];
                assertThat(source.getOrigin(), is(equalTo(serverSource.getOrigin())));
                assertThat(source.getName(), is(equalTo(serverSource.getName())));

                Observable<ChangeNotification<InstanceInfo>> updates = (Observable<ChangeNotification<InstanceInfo>>) invocation.getArguments()[1];
                updates.subscribe(registrySubject);
                return Observable.never();
            }
        });

        client = new FullFetchInterestClient2(clientSource, serverResolver, transportFactory, transportConfig, eurekaRegistry, RETRY_DELAY_MS, testScheduler);
    }

    @Test
    public void testInterestSubscriptionLifecycle() throws Exception {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        client.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);

        // There should be immediate change notification from server
        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(addChangeNotification()));

        // Disconnect transport
        transportHandler.disconnect();
        testScheduler.advanceTimeBy(RETRY_DELAY_MS, TimeUnit.MILLISECONDS);

        assertThat(testSubscriber.isUnsubscribed(), is(false));

        // ChangeNotification from new transport
        assertThat(testSubscriber.takeNext(), is(addChangeNotification()));
    }

    private class InterestHandlerStub implements InterestHandler {

        private final ChangeNotification<InstanceInfo> notification = new ChangeNotification<>(
                ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build()
        );

        private volatile PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replySubject;

        @Override
        public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> inputStream) {
            return Observable.create(subscriber -> {
                this.replySubject = PublishSubject.create();
                replySubject.subscribe(subscriber);

                inputStream.subscribe(
                        inputNotification -> {
                            if (inputNotification.getKind() == ChannelNotification.Kind.Hello) {
                                replySubject.onNext(ChannelNotification.newHello(
                                        TransportModel.getDefaultModel().newServerHello(serverSource)
                                ));
                            } else if (inputNotification.getKind() == ChannelNotification.Kind.Heartbeat) {
                                replySubject.onNext(ChannelNotification.newHeartbeat());
                            } else {
                                replySubject.onNext(ChannelNotification.newData(notification));
                            }
                        },
                        e -> {
                            replySubject.onError(e);
                            e.printStackTrace();
                        },
                        () -> {
                            System.out.println("Transport reply subscription terminated");
                            replySubject.onCompleted();
                        }
                );
            });
        }

        void disconnect() {
            replySubject.onError(new IOException("Simulated transport error"));
        }
    }
}