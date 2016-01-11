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

import com.netflix.eureka2.channel.ChannelTestkit;
import com.netflix.eureka2.channel.SourceIdGenerator;
import com.netflix.eureka2.channel.client.ClientHandshakeHandler;
import com.netflix.eureka2.client.channel.interest.InterestClientHandshakeHandler;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.model.ChannelModel;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka2.channel.ChannelTestkit.CHANNEL_INTEREST_NOTIFICATION_STREAM;
import static com.netflix.eureka2.channel.ChannelTestkit.SERVER_SOURCE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class ClientHandshakeHandlerTest {

    private static final ChangeNotification<InstanceInfo> CHANGE_NOTIFICATION = new ChangeNotification<>(ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build());
    private static final ChannelNotification<ChangeNotification<InstanceInfo>> CHANNEL_NOTIFICATION = ChannelNotification.newData(CHANGE_NOTIFICATION);

    private final InterestClientStub nextHandlerStub = new InterestClientStub();
    private final ClientHandshakeHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> handler = new InterestClientHandshakeHandler(ChannelTestkit.CLIENT_SOURCE, new SourceIdGenerator());

    private final ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("interest", handler, nextHandlerStub);
    }

    @Test
    public void testHandshake() throws Exception {
        handler.handle(CHANNEL_INTEREST_NOTIFICATION_STREAM).subscribe(testSubscriber);

        assertThat(nextHandlerStub.isHandshakeCompleted(), is(true));
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));
    }

    static class InterestClientStub implements InterestHandler {

        private volatile boolean handshakeCompleted;

        @Override
        public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> inputStream) {
            return inputStream.map(inputNotification -> {
                if (inputNotification.getKind() == ChannelNotification.Kind.Hello) {
                    handshakeCompleted = true;
                    return ChannelNotification.newHello(ChannelModel.getDefaultModel().newServerHello(SERVER_SOURCE));
                }
                return CHANNEL_NOTIFICATION;
            });
        }

        boolean isHandshakeCompleted() {
            return handshakeCompleted;
        }
    }
}
