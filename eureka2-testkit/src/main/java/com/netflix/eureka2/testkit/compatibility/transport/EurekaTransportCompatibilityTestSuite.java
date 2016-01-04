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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoField;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ModifyNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.utils.ExtCollections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.ReplaySubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public abstract class EurekaTransportCompatibilityTestSuite {

    private final InstanceInfo instance = SampleInstanceInfo.WebServer.build();
    private final Delta<?> updateDelta = InstanceModel.getDefaultModel().newDelta()
            .withId(instance.getId())
            .withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.DOWN)
            .build();
    private final InstanceInfo updatedInstance = instance.applyDelta(updateDelta);

    private TransportSession session;

    @Before
    public void setup() throws InterruptedException {
        session = new TransportSession(newClientTransportFactory(), newServerTransportFactory(), false);

        session.getInterestAcceptor().setReplyStream(
                new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instance),
                new ModifyNotification<InstanceInfo>(updatedInstance, Collections.singleton(updateDelta)),
                new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete, updatedInstance)
        );
    }

    @After
    public void tearDown() {
        if (session != null) {
            session.shutdown();
        }
    }

    protected abstract EurekaClientTransportFactory newClientTransportFactory();

    protected abstract EurekaServerTransportFactory newServerTransportFactory();

    @Test(timeout = 30000)
    public void testRegistrationHello() throws InterruptedException {
        RegistrationHandler clientTransport = session.createRegistrationClient();

        ReplaySubject<ChannelNotification<InstanceInfo>> registrations = ReplaySubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        clientTransport.handle(registrations).subscribe(testSubscriber);

        // Send hello
        registrations.onNext(ChannelNotification.newHello(session.getClientHello()));
        ChannelNotification<InstanceInfo> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getKind(), is(equalTo(ChannelNotification.Kind.Hello)));
        assertThat(helloReply.getHello(), is(equalTo(session.getServerHello())));
    }

    @Test(timeout = 30000)
    public void testRegistrationHeartbeat() throws InterruptedException {
        RegistrationHandler clientTransport = session.createRegistrationClient();

        ReplaySubject<ChannelNotification<InstanceInfo>> registrations = ReplaySubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        clientTransport.handle(registrations).subscribe(testSubscriber);

        // Send heartbeat
        registrations.onNext(ChannelNotification.newHeartbeat());
        ChannelNotification<InstanceInfo> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getKind(), is(equalTo(ChannelNotification.Kind.Heartbeat)));
    }

    @Test(timeout = 30000)
    public void testRegistrationConnection() throws InterruptedException {
        RegistrationHandler clientTransport = session.createRegistrationClient();

        ReplaySubject<ChannelNotification<InstanceInfo>> registrations = ReplaySubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        clientTransport.handle(registrations).subscribe(testSubscriber);

        // Send data
        registrations.onNext(ChannelNotification.newData(instance));
        ChannelNotification<InstanceInfo> confirmation = testSubscriber.takeNextOrWait();
        assertThat(confirmation.getKind(), is(equalTo(ChannelNotification.Kind.Data)));
    }

    @Test(timeout = 30000)
    public void testInterestHello() throws InterruptedException {
        InterestHandler clientTransport = session.createInterestClient();
        ReplaySubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        interestNotifications.onNext(ChannelNotification.newHello(session.getClientHello()));

        ChannelNotification<ChangeNotification<InstanceInfo>> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getHello(), is(equalTo(session.getServerHello())));
    }

    @Test(timeout = 30000)
    public void testInterestHeartbeat() throws InterruptedException {
        InterestHandler clientTransport = session.createInterestClient();
        ReplaySubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        interestNotifications.onNext(ChannelNotification.newHeartbeat());

        ChannelNotification<ChangeNotification<InstanceInfo>> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getKind(), is(equalTo(ChannelNotification.Kind.Heartbeat)));
    }

    @Test
    public void testInterestSubscription() throws InterruptedException {
        InterestHandler clientTransport = session.createInterestClient();
        ReplaySubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        interestNotifications.onNext(ChannelNotification.newHello(session.getClientHello()));
        interestNotifications.onNext(ChannelNotification.newData(session.getInterestModel().newFullRegistryInterest()));

        ChannelNotification<ChangeNotification<InstanceInfo>> notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(ChannelNotification.Kind.Hello)));

        // Sequence of buffeStart / add / modify /delete / bufferEnd
        ChannelNotification<ChangeNotification<InstanceInfo>> expectedBufferStart = testSubscriber.takeNextOrWait();
        assertThat(expectedBufferStart.getData().getKind(), is(equalTo(ChangeNotification.Kind.BufferSentinel)));
        StreamStateNotification<InstanceInfo> bufferStartUpdate = (StreamStateNotification<InstanceInfo>) expectedBufferStart.getData();
        assertThat(bufferStartUpdate.getBufferState(), is(equalTo(StreamStateNotification.BufferState.BufferStart)));

        ChannelNotification<ChangeNotification<InstanceInfo>> expectedAdd = testSubscriber.takeNextOrWait();
        assertThat(expectedAdd.getData().getKind(), is(equalTo(ChangeNotification.Kind.Add)));
        assertThat(expectedAdd.getData().getData(), is(equalTo(instance)));

        ChannelNotification<ChangeNotification<InstanceInfo>> expectedModify = testSubscriber.takeNextOrWait();
        assertThat(expectedModify.getData().getKind(), is(equalTo(ChangeNotification.Kind.Modify)));
        assertThat(expectedModify.getData().getData(), is(equalTo(updatedInstance)));

        ChannelNotification<ChangeNotification<InstanceInfo>> expectedDelete = testSubscriber.takeNextOrWait();
        assertThat(expectedDelete.getData().getKind(), is(equalTo(ChangeNotification.Kind.Delete)));
        assertThat(expectedDelete.getData().getData(), is(equalTo(updatedInstance)));

        ChannelNotification<ChangeNotification<InstanceInfo>> expectedBufferEnd = testSubscriber.takeNextOrWait();
        assertThat(expectedBufferEnd.getData().getKind(), is(equalTo(ChangeNotification.Kind.BufferSentinel)));
        StreamStateNotification<InstanceInfo> bufferEndUpdate = (StreamStateNotification<InstanceInfo>) expectedBufferEnd.getData();
        assertThat(bufferEndUpdate.getBufferState(), is(equalTo(StreamStateNotification.BufferState.BufferEnd)));
    }

    @Test
    public void testInterestCriteria() throws InterruptedException {
        testWithInterest(SampleInterest.DiscoveryInstance.build());
        testWithInterest(SampleInterest.DiscoveryApp.build());
        testWithInterest(SampleInterest.DiscoveryVip.build());
        testWithInterest(SampleInterest.DiscoveryVipSecure.build());
        testWithInterest(SampleInterest.MultipleApps.build());
        testWithInterest(Interests.forFullRegistry());
        testWithInterest(Interests.forNone());
    }

    private void testWithInterest(Interest<InstanceInfo> interest) throws InterruptedException {
        InterestHandler clientTransport = session.createInterestClient();
        Observable<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = Observable.just(ChannelNotification.newData(interest));

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        assertThat(session.getInterestAcceptor().getLastInterest(), is(equalTo(interest)));
    }

    @Test
    public void testDeltaUpdates() throws InterruptedException {
        InstanceInfo initialInstance = SampleInstanceInfo.Backend.build();
        DeltaBuilder builder = InstanceModel.getDefaultModel().newDelta().withId(initialInstance.getId());

        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.DOWN).build());

        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.APPLICATION, "newApp").build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.APPLICATION_GROUP, "newAppGroup").build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.ASG, "newAsg").build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.DATA_CENTER_INFO, SampleAwsDataCenterInfo.UsEast1a.build()).build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.HEALTHCHECK_URLS, ExtCollections.asSet("http://newHealthCheck1", "http://newHealthCheck2")).build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.HOMEPAGE_URL, "http://homepage").build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.STATUS_PAGE_URL, "http://statuspage").build());

        Map<String, String> metaData = new HashMap<>();
        metaData.put("key1", "value1");
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.META_DATA, metaData).build());

        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.PORTS, SampleServicePort.httpPorts()).build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.VIP_ADDRESS, "unsecureVip").build());
        testDeltaUpdate(initialInstance, builder.withDelta(InstanceInfoField.SECURE_VIP_ADDRESS, "secureVip").build());
    }

    private void testDeltaUpdate(InstanceInfo initialInstance, Delta<?> delta) throws InterruptedException {
        session.getInterestAcceptor().setReplyStream(
                new ChangeNotification<>(ChangeNotification.Kind.Add, initialInstance),
                new ModifyNotification<>(initialInstance.applyDelta(delta), Collections.singleton(delta))
        );

        InterestHandler clientTransport = session.createInterestClient();
        ReplaySubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        interestNotifications.onNext(ChannelNotification.newData(session.getInterestModel().newFullRegistryInterest()));

        testSubscriber.takeNextOrWait(); // Ignore buffer start
        testSubscriber.takeNextOrWait(); // Ignore add
        ChannelNotification<ChangeNotification<InstanceInfo>> reply = testSubscriber.takeNextOrWait();
        assertThat(reply.getKind(), is(equalTo(ChannelNotification.Kind.Data)));

        ModifyNotification<InstanceInfo> modify = (ModifyNotification<InstanceInfo>) reply.getData();
        assertThat(modify.getDelta().size(), is(equalTo(1)));

        Delta<?> repliedDelta = modify.getDelta().iterator().next();
        assertThat(repliedDelta, is(equalTo(delta)));
    }

    @Test(timeout = 30000)
    public void testReplicationHello() throws InterruptedException {
        ReplicationHandler clientTransport = session.createReplicationClient();
        ReplaySubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<Void>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(replicationUpdates).subscribe(testSubscriber);

        replicationUpdates.onNext(ChannelNotification.newHello(session.getReplicationClientHello()));
        ChannelNotification<Void> helloReply = testSubscriber.takeNextOrWait();

        assertThat(helloReply.getKind(), is(equalTo(ChannelNotification.Kind.Hello)));
        assertThat(helloReply.getHello(), is(equalTo(session.getReplicationServerHello())));
    }

    @Test(timeout = 30000)
    public void testReplicationHeartbeat() throws InterruptedException {
        ReplicationHandler clientTransport = session.createReplicationClient();
        ReplaySubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<Void>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(replicationUpdates).subscribe(testSubscriber);

        replicationUpdates.onNext(ChannelNotification.newHeartbeat());
        ChannelNotification<Void> heartbeatReply = testSubscriber.takeNextOrWait();

        assertThat(heartbeatReply.getKind(), is(equalTo(ChannelNotification.Kind.Heartbeat)));
    }

    @Test(timeout = 30000)
    public void testReplicationConnection() throws InterruptedException {
        ReplicationHandler clientTransport = session.createReplicationClient();
        ReplaySubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<Void>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(replicationUpdates).subscribe(testSubscriber);

        ChangeNotification<InstanceInfo> addNotification = new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instance);
        replicationUpdates.onNext(ChannelNotification.newHello(session.getReplicationClientHello()));
        replicationUpdates.onNext(ChannelNotification.newData(addNotification));

        assertThat(session.getReplicationAcceptor().takeNextUpdate().getKind(), is(equalTo(ChangeNotification.Kind.Add)));
    }
}
