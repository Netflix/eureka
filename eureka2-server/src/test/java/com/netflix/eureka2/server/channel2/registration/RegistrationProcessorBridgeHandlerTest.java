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

package com.netflix.eureka2.server.channel2.registration;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.channel.ChannelHandlers;
import com.netflix.eureka2.server.channel.registration.RegistrationProcessorBridgeHandler;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class RegistrationProcessorBridgeHandlerTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.Backend.build();
    private static final Source CLIENT_SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testClient");

    private ChannelNotification<InstanceInfo> channelNotification;

    private final EurekaRegistrationProcessorStub registrationProcessor = new EurekaRegistrationProcessorStub();

    private final RegistrationProcessorBridgeHandler handler = new RegistrationProcessorBridgeHandler(registrationProcessor);

    @Before
    public void setUp() throws Exception {
        channelNotification = ChannelNotification.newData(INSTANCE);
        channelNotification = ChannelHandlers.setClientSource(channelNotification, CLIENT_SOURCE);
    }

    @Test
    public void testRegistrationProcess() throws Exception {
        PublishSubject<ChannelNotification<InstanceInfo>> registrationUpdates = PublishSubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        Subscription subscription = handler.handle(registrationUpdates).subscribe(testSubscriber);

        // Send registration update
        registrationUpdates.onNext(channelNotification);
        assertThat(registrationProcessor.getConnectedClient(), is(equalTo(CLIENT_SOURCE)));
        assertThat(testSubscriber.takeNext().getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        // Check that unsubscribe sends unregister request, and closes all subscriptions
        subscription.unsubscribe();
        assertThat(registrationUpdates.hasObservers(), is(false));
        assertThat(registrationProcessor.getCollectedUpdates().size(), is(equalTo(2)));
        assertThat(registrationProcessor.getCollectedUpdates().get(1).getKind(), is(equalTo(ChangeNotification.Kind.Delete)));
    }

    @Test
    public void testRegistrationErrorIsPropagated() throws Exception {
        PublishSubject<ChannelNotification<InstanceInfo>> registrationUpdates = PublishSubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        Subscription subscription = handler.handle(registrationUpdates).subscribe(testSubscriber);

        // Send registration update, to force processor connect
        registrationUpdates.onNext(channelNotification);

        // Simulate transport error
        registrationUpdates.onError(new IOException("Simulated transport error"));
        assertThat(registrationProcessor.getErrorCounter(), is(equalTo(1)));
        assertThat(subscription.isUnsubscribed(), is(true));
    }

    static class EurekaRegistrationProcessorStub implements EurekaRegistrationProcessor<InstanceInfo> {

        private volatile Source connectedClient;
        private volatile int errorCounter;
        private final List<ChangeNotification<InstanceInfo>> collectedUpdates = new CopyOnWriteArrayList<>();

        @Override
        public Observable<Void> connect(String id, Source source, Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
            this.connectedClient = source;

            Subscription subscription = registrationUpdates.subscribe(
                    next -> collectedUpdates.add(next),
                    e -> errorCounter++,
                    () -> System.out.println("RegistrationProcess input stream onCompleted")
            );

            return Observable.<Void>never().doOnUnsubscribe(() -> subscription.unsubscribe());
        }

        @Override
        public Observable<Integer> sizeObservable() {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Observable<Void> shutdown() {
            return null;
        }

        @Override
        public Observable<Void> shutdown(Throwable cause) {
            return null;
        }

        public Source getConnectedClient() {
            return connectedClient;
        }

        public int getErrorCounter() {
            return errorCounter;
        }

        public List<ChangeNotification<InstanceInfo>> getCollectedUpdates() {
            return collectedUpdates;
        }
    }
}