/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.server.transport.registration.protocol.asynchronous;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.transport.Context;
import com.netflix.eureka.server.transport.registration.RegistrationHandler;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Observable;

import static com.netflix.eureka.SampleInstanceInfo.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class AsyncRegistrationServerTest {

    private MessageBrokerServer brokerServer;
    private AsyncRegistrationServer registrationServer;
    private MessageBroker brokerClient;
    private TestRegistrationHandler handler = new TestRegistrationHandler();

    @Before
    public void setUp() throws Exception {
        brokerServer = EurekaTransports.tcpRegistrationServer(0, Codec.Json).start();
        registrationServer = new AsyncRegistrationServer(brokerServer, handler);
        brokerClient = EurekaTransports.tcpRegistrationClient("localhost", brokerServer.getServerPort(), Codec.Json).toBlocking().first();
    }

    @After
    public void tearDown() throws Exception {
        if (registrationServer != null) {
            registrationServer.shutdown();
        }
    }

    @Test
    public void testRegister() throws Exception {
        Register request = new Register(ZuulServer.build());
        Observable<Void> ackObservable = brokerClient.submitWithAck(request);
        Notification<Void> ackNotification = ackObservable.materialize().toBlocking().single();
        assertTrue("Acknowledgement failure", ackNotification.isOnCompleted());

        assertEquals("Invalid registration instanceInfo received", request.getInstanceInfo(), handler.registerRef.get());
    }

    @Test
    public void testUpdate() throws Exception {
        Update request = new Update(DiscoveryServer.build());
        Observable<Void> ackObservable = brokerClient.submitWithAck(request);
        Notification<Void> ackNotification = ackObservable.materialize().toBlocking().single();
        assertTrue("Acknowledgement failure", ackNotification.isOnCompleted());

        assertEquals("Invalid registration instanceInfo received", request, handler.updateRef.get());
    }

    @Test
    public void testUnregister() throws Exception {
        Unregister unregister = new Unregister();
        Observable<Void> ackObservable = brokerClient.submitWithAck(unregister);
        Notification<Void> ackNotification = ackObservable.materialize().toBlocking().single();
        assertTrue("Acknowledgement failure", ackNotification.isOnCompleted());

        assertTrue("Unregister request not received", handler.unregisteredRef.get());
    }

    @Test
    public void testHeartbeat() throws Exception {
        brokerClient.submit(Heartbeat.INSTANCE);
        assertTrue("Heartbeat not delivered", handler.heartbeatLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    static class TestRegistrationHandler implements RegistrationHandler {

        AtomicReference<InstanceInfo> registerRef = new AtomicReference<InstanceInfo>();
        AtomicReference<Update> updateRef = new AtomicReference<Update>();
        AtomicBoolean unregisteredRef = new AtomicBoolean();
        CountDownLatch heartbeatLatch = new CountDownLatch(1);

        @Override
        public Observable<Void> register(Context context, InstanceInfo instanceInfo) {
            registerRef.set(instanceInfo);
            return Observable.empty();
        }

        @Override
        public Observable<Void> update(Context context, Update update) {
            updateRef.set(update);
            return Observable.empty();
        }

        @Override
        public Observable<Void> unregister(Context context) {
            unregisteredRef.set(true);
            return Observable.empty();
        }

        @Override
        public Observable<Void> heartbeat(Context context) {
            heartbeatLatch.countDown();
            return Observable.empty();
        }

        @Override
        public void shutdown() {
        }
    }
}