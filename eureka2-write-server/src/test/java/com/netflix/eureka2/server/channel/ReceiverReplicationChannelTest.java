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

package com.netflix.eureka2.server.channel;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.channel.ReplicationChannel.STATE;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.DeleteInstance;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class ReceiverReplicationChannelTest extends AbstractReplicationChannelTest {

    private final MessageConnection transport = mock(MessageConnection.class);
    private final PublishSubject<Void> transportLifeCycle = PublishSubject.create();

    private final SelfInfoResolver SelfIdentityService = mock(SelfInfoResolver.class);
    private final EurekaRegistry<InstanceInfo> registry = mock(EurekaRegistry.class);
    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> dataSubscriber = new ExtTestSubscriber<>();
    private final TestSubscriber<Void> lifecycleSubscriber = new TestSubscriber<>();

    private ReceiverReplicationChannel replicationChannel;
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();
    private final ReplicationChannelMetrics channelMetrics = mock(ReplicationChannelMetrics.class);

    // Argument captors
    private final ArgumentCaptor<InstanceInfo> infoCaptor = ArgumentCaptor.forClass(InstanceInfo.class);
    private final ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);

    @Before
    public void setUp() throws Exception {
        when(transport.lifecycleObservable()).thenReturn(transportLifeCycle);
        when(transport.incoming()).thenReturn(incomingSubject);
        when(transport.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(transport.acknowledge()).thenReturn(Observable.<Void>empty());

        when(SelfIdentityService.resolve()).thenReturn(Observable.just(RECEIVER_INFO));

        final ReplaySubject<ChangeNotification<InstanceInfo>> dataSubject = ReplaySubject.create();
        when(registry.connect(any(Source.class), any(Observable.class))).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                Observable<ChangeNotification<InstanceInfo>> dataStream = (Observable<ChangeNotification<InstanceInfo>>) invocation.getArguments()[1];
                dataStream.subscribe(dataSubject);
                dataSubject.subscribe(dataSubscriber);
                return Observable.never();
            }
        });
        when(registry.forInterest(any(Interest.class), any(Source.SourceMatcher.class))).thenAnswer(new Answer<Observable<ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<ChangeNotification<InstanceInfo>> answer(InvocationOnMock invocation) throws Throwable {
                return dataSubject;
            }
        });

        replicationChannel = new ReceiverReplicationChannel(transport, SelfIdentityService, registry, channelMetrics);
    }

    @After
    public void tearDown() throws Exception {
        replicationChannel.close();
    }

    @Test(timeout = 60000)
    public void testHandlesHello() throws Exception {
        incomingSubject.onNext(HELLO);

        ArgumentCaptor<ReplicationHelloReply> captor = ArgumentCaptor.forClass(ReplicationHelloReply.class);
        verify(transport).submit(captor.capture());
        ReplicationHelloReply reply = captor.getValue();

        assertThat(reply.getSource().getName(), is(equalTo(HELLO_REPLY.getSource().getName())));
    }

    @Test(timeout = 60000)
    public void testHandlesRegistration() throws Exception {
        handshakeAndRegister(APP_INFO);

        ChangeNotification<InstanceInfo> notification = dataSubscriber.takeNext(2, TimeUnit.SECONDS);
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Add));
        assertThat(notification.getData(), is(APP_INFO));
        assertThat(((Sourced)notification).getSource(), is(replicationChannel.getSource()));
    }

    @Test(timeout = 60000)
    public void testHandlesRegisterThatIsAnUpdate() throws Exception {
        handshakeAndRegister(APP_INFO);

        ChangeNotification<InstanceInfo> notification = dataSubscriber.takeNext(2, TimeUnit.SECONDS);
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Add));
        assertThat(notification.getData(), is(APP_INFO));

        // Now update the record
        InstanceInfo infoUpdate = new InstanceInfo.Builder().withInstanceInfo(APP_INFO).withApp("myNewName").build();
        incomingSubject.onNext(new AddInstance(infoUpdate));

        notification = dataSubscriber.takeNext(2, TimeUnit.SECONDS);
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Add));
        assertThat(notification.getData(), is(infoUpdate));
        assertThat(((Sourced) notification).getSource(), is(replicationChannel.getSource()));
    }

    @Test(timeout = 60000)
    public void testHandlesUnregister() throws Exception {
        handshakeAndRegister(APP_INFO);

        ChangeNotification<InstanceInfo> notification = dataSubscriber.takeNext(2, TimeUnit.SECONDS);
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Add));
        assertThat(notification.getData(), is(APP_INFO));

        // Now remove the record
        incomingSubject.onNext(new DeleteInstance(APP_INFO.getId()));

        notification = dataSubscriber.takeNext(2, TimeUnit.SECONDS);
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Delete));
        assertThat(notification.getData(), is(APP_INFO));
        assertThat(((Sourced) notification).getSource(), is(replicationChannel.getSource()));
    }

    @Test(timeout = 60000)
    public void testDetectsReplicationLoop() throws Exception {
        ReplicationHello hello = new ReplicationHello(RECEIVER_SOURCE, 0);
        incomingSubject.onNext(hello);

        replicationChannel.asLifecycleObservable().subscribe(lifecycleSubscriber);
        lifecycleSubscriber.assertCompleted();
    }

    @Test(timeout = 60000)
    public void testMetrics() throws Exception {
        // Idle -> Handshake -> Connected
        incomingSubject.onNext(HELLO);

        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Handshake);
        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Connected);

        // Shutdown channel (Connected -> Closed)
        replicationChannel.close();
        verify(channelMetrics, times(1)).decrementStateCounter(STATE.Connected);
        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Closed);
    }

    protected void handshakeAndRegister(InstanceInfo info) {
        incomingSubject.onNext(HELLO);
        incomingSubject.onNext(new AddInstance(info));
    }
}