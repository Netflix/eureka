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

package com.netflix.eureka2.server.service.replication;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ReplicationChannel;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class RegistryReplicatorTest {

    private static final TestScheduler testScheduler = Schedulers.test();

    private static final InstanceInfo SELF_INFO = SampleInstanceInfo.DiscoveryServer.build();
    private static final InstanceInfo INSTANCE_INFO = SampleInstanceInfo.ZuulServer.build();

    public static final ReplicationHello HELLO = new ReplicationHello(SELF_INFO.getId(), 1);

    private final ReplicationChannel channel = mock(ReplicationChannel.class);

    private final SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics(), testScheduler);
    private RegistryReplicator replicator;

    @Before
    public void setUp() throws Exception {
        replicator = new RegistryReplicator(SELF_INFO.getId(), registry);

        registry.register(INSTANCE_INFO).subscribe();
        testScheduler.triggerActions();
    }

    @After
    public void tearDown() throws Exception {
        replicator.close();
    }

    @Test
    public void testReplicatesRegistryContent() throws Exception {
        ReplicationHelloReply helloReply = new ReplicationHelloReply(INSTANCE_INFO.getId(), false);

        when(channel.hello(HELLO)).thenReturn(Observable.just(helloReply));
        when(channel.register(any(InstanceInfo.class))).thenReturn(Observable.<Void>empty());

        replicator.reconnect(channel);
        verify(channel, times(1)).hello(HELLO);
        verify(channel, times(1)).register(INSTANCE_INFO);

        // Trigger update
        when(channel.update(any(InstanceInfo.class))).thenReturn(Observable.<Void>empty());

        InstanceInfo updateInfo = new InstanceInfo.Builder().withInstanceInfo(INSTANCE_INFO).withAsg("newAsg").build();

        registry.update(updateInfo, updateInfo.diffOlder(updateInfo)).subscribe();
        testScheduler.triggerActions();

        verify(channel, times(1)).update(updateInfo);

        // Trigger remove
        when(channel.unregister(anyString())).thenReturn(Observable.<Void>empty());

        registry.unregister(updateInfo).subscribe();
        testScheduler.triggerActions();

        verify(channel, times(1)).unregister(updateInfo.getId());
    }

    @Test
    public void testDisconnectsWhenConnectedToItself() throws Exception {
        ReplicationHelloReply helloReply = new ReplicationHelloReply(SELF_INFO.getId(), false);

        when(channel.hello(HELLO)).thenReturn(Observable.just(helloReply));

        replicator.reconnect(channel);
        verify(channel, times(1)).close();
    }
}