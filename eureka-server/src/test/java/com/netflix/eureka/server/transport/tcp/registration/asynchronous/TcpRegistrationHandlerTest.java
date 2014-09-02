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

package com.netflix.eureka.server.transport.tcp.registration.asynchronous;

/**
 * @author Tomasz Bak
 */
public class TcpRegistrationHandlerTest {
/*

    private final TestableEurekaService eurekaService = new TestableEurekaService();

    private final TestableObservableConnection<Object, Object> observableConnection = new TestableObservableConnection<Object, Object>();

    private final TcpRegistrationHandler registrationHandler = new TcpRegistrationHandler(eurekaService);

    private final Observable<Void> handleObservable = registrationHandler.handle(observableConnection);

    private final RecordingSubscriber<Void> handlerStatus = RecordingSubscriber.subscribeTo(handleObservable);

    @Test(timeout = 10000)
    public void testRegistrationAndUnregistration() throws Exception {
        // Register
        TestableRegistrationChannel registrationChannel = doRegister();

        // Unregister
        observableConnection.testableChannelRead().onNext(new Unregister());
        assertTrue("Interest channel shall be closed by now", RxBlocking.isCompleted(1, TimeUnit.SECONDS, registrationChannel.viewClose()));
    }

    @Test(timeout = 10000)
    public void testClientDisconnect() throws Exception {
        // Register
        TestableRegistrationChannel registrationChannel = doRegister();

        // Simulate client disconnect.
        handlerStatus.getSubscription().unsubscribe();
        assertTrue("Channel should be closed by now", RxBlocking.isCompleted(1, TimeUnit.SECONDS, registrationChannel.viewClose()));
    }

    @Test(timeout = 10000)
    public void testUpdate() throws Exception {
        Builder instanceInfoBuilder = DiscoveryServer.builder();

        // Register
        TestableRegistrationChannel registrationChannel = doRegister();

        // Update
        InstanceInfo updated = instanceInfoBuilder.withApp("my_new_app").build();
        observableConnection.testableChannelRead().onNext(new Update(updated));

        Object storedUpdate = registrationChannel.viewUpdates().poll(1, TimeUnit.SECONDS);
        assertEquals("Updated record differes from the one in update request", updated, storedUpdate);
    }

    @Test(timeout = 10000)
    public void testHeartbeat() throws Exception {
        // Register
        TestableRegistrationChannel registrationChannel = doRegister();

        // Heartbeat
        observableConnection.testableChannelRead().onNext(new Heartbeat());
        Long heartbeatTime = registrationChannel.viewHeartbeats().poll(1, TimeUnit.SECONDS);
        assertNotNull("Heartbeat not received", heartbeatTime);
    }

    private TestableRegistrationChannel doRegister() throws InterruptedException {
        observableConnection.testableChannelRead().onNext(new Register(DiscoveryServer.build()));
        TestableRegistrationChannel registrationChannel = (TestableRegistrationChannel) eurekaService.viewNewRegistrationChannels().poll(1, TimeUnit.SECONDS);
        assertTrue("Active connection expected", !handlerStatus.isDone());
        assertNotNull("Expected registered channel instance", registrationChannel);
        return registrationChannel;
    }
*/
}