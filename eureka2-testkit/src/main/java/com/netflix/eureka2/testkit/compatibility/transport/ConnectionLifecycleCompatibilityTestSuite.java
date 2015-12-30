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

package com.netflix.eureka2.testkit.compatibility.transport;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.junit.ExtAsserts;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.eureka2.transport.TransportDisconnected;
import org.junit.After;
import org.junit.Test;
import rx.subjects.ReplaySubject;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * Test network connection level behaviors, to make sure that client and server side are properly completed or
 * expected errors are propagated. This test suite uses network router functionality {@link NetworkRouter}, to
 * force TCP level disconnects.
 */
public abstract class ConnectionLifecycleCompatibilityTestSuite {

    private TransportSession session;

    @After
    public void tearDown() {
        if (session != null) {
            session.shutdown();
        }
    }

    protected abstract EurekaClientTransportFactory newClientTransportFactory();

    protected abstract EurekaServerTransportFactory newServerTransportFactory();

    @Test(timeout = 30000)
    public void testRegistrationServerShutdownCompletesClient() throws Exception {
        session = new TransportSession(newClientTransportFactory(), newServerTransportFactory(), false);
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = establishRegistrationConnection();

        session.shutdown();
        testSubscriber.assertOnError(TransportDisconnected.class, 50, TimeUnit.SECONDS);
    }

    @Test(timeout = 30000)
    public void testRegistrationClientDisconnectCompletesServerSideConnection() throws Exception {
        session = new TransportSession(newClientTransportFactory(), newServerTransportFactory(), false);
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = establishRegistrationConnection();

        assertThat(session.getRegistrationAcceptor().getActiveConnectionCount(), is(equalTo(1)));

        testSubscriber.unsubscribe();

        ExtAsserts.assertThat(() -> session.getRegistrationAcceptor().getActiveConnectionCount(), 0, 5, TimeUnit.SECONDS);
    }

    @Test(timeout = 30000)
    public void testNetworkFailureDisconnectsRegistrationClientAndServer() throws Exception {
        session = new TransportSession(newClientTransportFactory(), newServerTransportFactory(), true);
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = establishRegistrationConnection();

        assertThat(session.getRegistrationAcceptor().getActiveConnectionCount(), is(equalTo(1)));

        session.injectNetworkFailure();

        testSubscriber.assertOnError(5, TimeUnit.SECONDS);
        ExtAsserts.assertThat(() -> session.getRegistrationAcceptor().getActiveConnectionCount(), 0, 5, TimeUnit.SECONDS);
    }

    @Test(timeout = 30000)
    public void testInterestServerShutdownCompletesClient() throws Exception {
        session = new TransportSession(newClientTransportFactory(), newServerTransportFactory(), false);
        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = establishInterestConnection();

        session.shutdown();
        testSubscriber.assertOnError(TransportDisconnected.class, 5, TimeUnit.SECONDS);
    }

    @Test(timeout = 30000)
    public void testInterestClientDisconnectCompletesServerSideConnection() throws Exception {
        session = new TransportSession(newClientTransportFactory(), newServerTransportFactory(), false);

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = establishInterestConnection();
        assertThat(session.getInterestAcceptor().getActiveConnectionCount(), is(equalTo(1)));

        testSubscriber.unsubscribe();

        ExtAsserts.assertThat(() -> session.getInterestAcceptor().getActiveConnectionCount(), 0, 5, TimeUnit.SECONDS);
    }

    @Test(timeout = 30000)
    public void testNetworkFailureDisconnectsInterestClientAndServer() throws Exception {
        session = new TransportSession(newClientTransportFactory(), newServerTransportFactory(), true);

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = establishInterestConnection();
        assertThat(session.getInterestAcceptor().getActiveConnectionCount(), is(equalTo(1)));

        session.injectNetworkFailure();

        testSubscriber.assertOnError(5, TimeUnit.SECONDS);
        ExtAsserts.assertThat(() -> session.getInterestAcceptor().getActiveConnectionCount(), 0, 5, TimeUnit.SECONDS);
    }

    private ExtTestSubscriber<ChannelNotification<InstanceInfo>> establishRegistrationConnection() throws InterruptedException {
        ReplaySubject<ChannelNotification<InstanceInfo>> registrationUpdates = ReplaySubject.create().create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        session.createRegistrationClient().handle(registrationUpdates).subscribe(testSubscriber);

        registrationUpdates.onNext(ChannelNotification.newData(SampleInstanceInfo.Backend.build()));
        assertThat(testSubscriber.takeNext(5, TimeUnit.SECONDS), is(notNullValue()));

        return testSubscriber;
    }

    private ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> establishInterestConnection() throws InterruptedException {
        InterestHandler clientTransport = session.createInterestClient();
        ReplaySubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        // Exchange hello messages to be sure that connection is fully operational
        interestNotifications.onNext(ChannelNotification.newHello(session.getClientHello()));

        ChannelNotification<ChangeNotification<InstanceInfo>> notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(ChannelNotification.Kind.Hello)));
        return testSubscriber;
    }
}
