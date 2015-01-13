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

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.server.metric.WriteServerMetricFactory.writeServerMetrics;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class ReceiverReplicationChannelTest extends AbstractReplicationChannelTest {

    private final MessageConnection transport = mock(MessageConnection.class);
    private final PublishSubject<Void> transportLifeCycle = PublishSubject.create();

    private final SelfInfoResolver SelfIdentityService = mock(SelfInfoResolver.class);
    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);
    private final EvictionQueue evictionQueue = mock(EvictionQueue.class);

    private ReceiverReplicationChannel replicationChannel;
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();

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

        replicationChannel = new ReceiverReplicationChannel(transport, SelfIdentityService, registry,
                evictionQueue, writeServerMetrics().getReplicationChannelMetrics());
    }

    @After
    public void tearDown() throws Exception {
        replicationChannel.close();
    }

    @Test
    public void testHandlesHello() throws Exception {
        incomingSubject.onNext(HELLO);

        ArgumentCaptor<ReplicationHelloReply> captor = ArgumentCaptor.forClass(ReplicationHelloReply.class);
        verify(transport).submit(captor.capture());
        ReplicationHelloReply reply = captor.getValue();

        assertThat(reply, is(equalTo(HELLO_REPLY)));
    }

    @Test
    public void testHandlesRegistration() throws Exception {
        handshakeAndRegister(APP_INFO);

        // Capture registration on the registry and verify the arguments
        verify(registry, times(1)).register(infoCaptor.capture(), sourceCaptor.capture());
        verifyInstanceAndSourceCaptures(APP_INFO, SENDER_ID);
    }

    @Test
    public void testHandlesRegisterThatIsAnUpdate() throws Exception {
        handshakeAndRegister(APP_INFO);

        // Now update the record
        InstanceInfo infoUpdate = new InstanceInfo.Builder().withInstanceInfo(APP_INFO).withApp("myNewName").build();

        when(registry.register(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.just(false));
        incomingSubject.onNext(new RegisterCopy(infoUpdate));

        verify(registry, times(2)).register(infoCaptor.capture(), sourceCaptor.capture());

        List<InstanceInfo> capturedInfos = new ArrayList<>();
        // reset the versions in the captured to -1 as they will have been stamped by the channel
        for (InstanceInfo captured : infoCaptor.getAllValues()) {
            capturedInfos.add(new InstanceInfo.Builder().withInstanceInfo(captured).build());
        }

        assertThat(capturedInfos, contains(APP_INFO, infoUpdate));

        // Verify
        verifyInstanceAndSourceCaptures(infoUpdate, SENDER_ID);
    }

    @Test
    public void testHandlesUnregister() throws Exception {
        handshakeAndRegister(APP_INFO);

        // Now remove the record
        when(registry.unregister(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.just(true));
        incomingSubject.onNext(new UnregisterCopy(APP_INFO.getId()));

        // Capture remove on the registry and verify the arguments
        verify(registry, times(1)).unregister(infoCaptor.capture(), sourceCaptor.capture());
        verifyInstanceAndSourceCaptures(APP_INFO, SENDER_ID);
    }

    @Test
    public void testDetectsReplicationLoop() throws Exception {
        ReplicationHello hello = new ReplicationHello(RECEIVER_ID, 0);
        incomingSubject.onNext(hello);

        incomingSubject.onNext(new RegisterCopy(APP_INFO));
        incomingSubject.onNext(new RegisterCopy(APP_INFO));  // this is an update
        incomingSubject.onNext(new UnregisterCopy(APP_INFO.getId()));
        verify(transport, times(3)).onError(ReceiverReplicationChannel.REPLICATION_LOOP_EXCEPTION);
    }

    @Test
    public void testAddsRecordsToEvictionQueueOnTransportDisconnect() throws Exception {
        verifyAddsRecordsToEvictionQueueOnDisconnect(false);
    }

    @Test
    public void testAddsRecordsToEvictionQueueOnTransportError() throws Exception {
        verifyAddsRecordsToEvictionQueueOnDisconnect(true);
    }

    public void verifyAddsRecordsToEvictionQueueOnDisconnect(boolean onError) throws Exception {
        handshakeAndRegister(APP_INFO);

        // Now disconnect
        if (onError) {
            transportLifeCycle.onError(new Exception("disconnect with error"));
        } else {
            transportLifeCycle.onCompleted();
        }

        // Verify
        verify(evictionQueue, times(1)).add(infoCaptor.capture(), sourceCaptor.capture());
        verifyInstanceAndSourceCaptures(APP_INFO, SENDER_ID);
    }

    protected void handshakeAndRegister(InstanceInfo info) {
        incomingSubject.onNext(HELLO);

        when(registry.register(any(InstanceInfo.class), any(Source.class))).thenReturn(Observable.just(false));
        incomingSubject.onNext(new RegisterCopy(info));
    }

    private void verifyInstanceAndSourceCaptures(InstanceInfo info, String senderId) {
        assertThat(infoCaptor.getValue().getId(), is(equalTo(info.getId())));
        assertThat(sourceCaptor.getValue().getId(), is(equalTo(replicationChannel.getSource().getId())));
        assertThat(replicationChannel.getSource().getName().contains(senderId), is(true));
    }
}