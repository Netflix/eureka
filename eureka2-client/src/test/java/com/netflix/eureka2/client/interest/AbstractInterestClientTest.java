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

import java.io.IOException;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.model.ChannelModel;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
public class AbstractInterestClientTest {

    protected static final long RETRY_DELAY_MS = 1000;

    protected final Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testClient");
    protected final Source serverSource = InstanceModel.getDefaultModel().createSource(Source.Origin.INTERESTED, "testServer");

    protected final TestScheduler testScheduler = Schedulers.test();

    protected final ServerResolver serverResolver = mock(ServerResolver.class);

    protected final EurekaClientTransportFactory transportFactory = mock(EurekaClientTransportFactory.class);
    protected final EurekaTransportConfig transportConfig = mock(EurekaTransportConfig.class);

    protected final InterestHandlerStub transportHandler = new InterestHandlerStub();

    @Before
    public void setUp() throws Exception {
        // Server resolver
        when(serverResolver.resolve()).thenReturn(Observable.just(new Server("testHost", 123)));

        // Transport
        when(transportConfig.getHeartbeatIntervalMs()).thenReturn(30000L);
        when(transportFactory.newInterestTransport(any())).thenReturn(transportHandler);
    }

    protected void setupEurekaRegistryConnect(EurekaRegistry<InstanceInfo> eurekaRegistry, PublishSubject<ChangeNotification<InstanceInfo>> registrySubject) {
        // Eureka registry
        when(eurekaRegistry.forInterest(any())).thenReturn(registrySubject);
        when(eurekaRegistry.connect(any(), any())).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                Source source = (Source) invocation.getArguments()[0];
                assertThat(source.getOrigin(), is(equalTo(serverSource.getOrigin())));
                assertThat(source.getName(), is(equalTo(serverSource.getName())));

                Observable<ChangeNotification<InstanceInfo>> updates = (Observable<ChangeNotification<InstanceInfo>>) invocation.getArguments()[1];
                Subscription subscription = updates.subscribe(registrySubject);
                return Observable.<Void>never().doOnUnsubscribe(() -> subscription.unsubscribe());
            }
        });
    }

    protected class InterestHandlerStub implements InterestHandler {

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
                PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> myReplySubject = PublishSubject.create();
                myReplySubject.subscribe(subscriber);

                this.replySubject = myReplySubject; // Reference to the last one that was not onError-ed

                inputStream.subscribe(
                        inputNotification -> {
                            if (inputNotification.getKind() == ChannelNotification.Kind.Hello) {
                                myReplySubject.onNext(ChannelNotification.newHello(
                                        ChannelModel.getDefaultModel().newServerHello(serverSource)
                                ));
                            } else if (inputNotification.getKind() == ChannelNotification.Kind.Heartbeat) {
                                myReplySubject.onNext(ChannelNotification.newHeartbeat());
                            } else {
                                Interest<InstanceInfo> interest = inputNotification.getData();
                                myReplySubject.onNext(ChannelNotification.newData(StreamStateNotification.bufferStartNotification(interest)));
                                myReplySubject.onNext(ChannelNotification.newData(notification));
                                myReplySubject.onNext(ChannelNotification.newData(StreamStateNotification.bufferEndNotification(interest)));
                            }
                        },
                        e -> {
                            myReplySubject.onError(e);
                            e.printStackTrace();
                        },
                        () -> {
                            System.out.println("Transport reply subscription terminated");
                            myReplySubject.onCompleted();
                        }
                );
            });
        }

        void disconnect() {
            replySubject.onError(new IOException("Simulated transport error"));
        }
    }
}
