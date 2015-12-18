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

package com.netflix.eureka2.server.channel2.replication;

import com.netflix.eureka2.channel2.SourceIdGenerator;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.client.channel2.ChannelTestkit.CLIENT_SOURCE;
import static com.netflix.eureka2.client.channel2.ChannelTestkit.SERVER_SOURCE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

/**
 */
public class SenderReplicationHandshakeHandlerTest {

    private final EurekaRegistry<InstanceInfo> eurekaRegistry = mock(EurekaRegistry.class);
    private final SenderReplicationHandshakeHandler handler = new SenderReplicationHandshakeHandler(CLIENT_SOURCE, new SourceIdGenerator(), eurekaRegistry);

    private final ReplicationHandlerStub nextHandlerStub = new ReplicationHandlerStub(SERVER_SOURCE);

    private final ExtTestSubscriber<ChannelNotification<Void>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        new ChannelPipeline<>("replication", handler, nextHandlerStub);
    }

    @Test
    public void testHandshake() throws Exception {
        ReplaySubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationUpdates = ReplaySubject.create();
        handler.handle(replicationUpdates).subscribe(testSubscriber);

        // First update triggers handshake
        ChangeNotification<InstanceInfo> addChange = new ChangeNotification<>(ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build());
        replicationUpdates.onNext(ChannelNotification.newData(addChange));

        testSubscriber.assertOpen();
        assertThat(nextHandlerStub.isHandshakeCompleted(), is(true));
        assertThat(nextHandlerStub.getCollectedChanges(), is(equalTo(1)));
    }
}