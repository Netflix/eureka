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

import com.netflix.eureka2.channel.SourceIdGenerator;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ServerHandshakeHandler;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.model.ChannelModel;
import com.netflix.eureka2.spi.model.channel.ClientHello;
import com.netflix.eureka2.spi.model.channel.ServerHello;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class ServerHandshakeHandlerTest {

    private static final Source CLIENT_SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testClient");
    private static final Source SERVER_SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testServer");
    private static final ClientHello CLIENT_HELLO = ChannelModel.getDefaultModel().newClientHello(CLIENT_SOURCE);
    private static final ServerHello SERVER_HELLO = ChannelModel.getDefaultModel().newServerHello(SERVER_SOURCE);

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.Backend.build();

    private final ChannelHandlerStub nextHandler = new ChannelHandlerStub();

    private final ServerHandshakeHandler<InstanceInfo, InstanceInfo> handler = new ServerHandshakeHandler<>(
            SERVER_HELLO,
            new SourceIdGenerator()
    );

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("handshake", handler, nextHandler);
    }

    @Test
    public void testHandshake() throws Exception {
        PublishSubject<ChannelNotification<InstanceInfo>> inputStream = PublishSubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        Subscription subscription = handler.handle(inputStream).subscribe(testSubscriber);

        // Check hello
        inputStream.onNext(ChannelNotification.newHello(CLIENT_HELLO));
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Hello)));

        // Check data is passed transparently
        inputStream.onNext(ChannelNotification.newData(INSTANCE));
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        // Check that unsubscribe closes all subscriptions
        subscription.unsubscribe();
        assertThat(inputStream.hasObservers(), is(false));
    }

    private static class ChannelHandlerStub implements ChannelHandler<InstanceInfo, InstanceInfo> {
        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {

        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> inputStream) {
            return inputStream.filter(notification -> notification.getKind() != ChannelNotification.Kind.Hello);
        }
    }
}