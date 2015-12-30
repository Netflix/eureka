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

import com.netflix.eureka2.client.channel.register.RegistrationClientHandshakeHandler;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.channel.ChannelTestkit.CHANNEL_INSTANCE_NOTIFICATION;
import static com.netflix.eureka2.channel.ChannelTestkit.SERVER_SOURCE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class RegistrationClientHandshakeHandlerTest {

    private final RegistrationHandlerStub nextHandlerStub = new RegistrationHandlerStub();
    private final RegistrationClientHandshakeHandler handler = new RegistrationClientHandshakeHandler();

    private final ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("registration", handler, nextHandlerStub);
    }

    @Test
    public void testHandshake() throws Exception {
        ReplaySubject<ChannelNotification<InstanceInfo>> registrationUpdates = ReplaySubject.create();
        handler.handle(registrationUpdates).subscribe(testSubscriber);

        registrationUpdates.onNext(CHANNEL_INSTANCE_NOTIFICATION);

        assertThat(nextHandlerStub.isHandshakeCompleted(), is(true));
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));
    }

    static class RegistrationHandlerStub implements RegistrationHandler {

        private volatile boolean handshakeCompleted;

        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        }

        boolean isHandshakeCompleted() {
            return handshakeCompleted;
        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
            return registrationUpdates.map(inputNotification -> {
                if (inputNotification.getKind() == ChannelNotification.Kind.Hello) {
                    handshakeCompleted = true;
                    return ChannelNotification.newHello(SERVER_SOURCE);
                }
                return CHANNEL_INSTANCE_NOTIFICATION;
            });
        }
    }
}