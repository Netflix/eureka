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

import java.net.InetSocketAddress;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.channel.ReplicationChannel;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka2.server.metric.WriteServerMetricFactory.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class ReplicationServiceTest {

    private static final InstanceInfo SELF_INFO = SampleInstanceInfo.DiscoveryServer.build();

    private static final InetSocketAddress ADDRESS = new InetSocketAddress("host1", 123);

    private final WriteServerConfig config = WriteServerConfig.writeBuilder().build();
    private final SourcedEurekaRegistry<InstanceInfo> eurekaRegistry = mock(SourcedEurekaRegistry.class);
    private final SelfRegistrationService selfRegistrationService = mock(SelfRegistrationService.class);

    private final ReplicationPeerAddressesProvider peerAddressProvider = mock(ReplicationPeerAddressesProvider.class);

    private final ReplicationChannel replicationChannel = mock(ReplicationChannel.class);

    private ReplicationService replicationService;

    private InetSocketAddress lastAddedAddress;

    @Before
    public void setUp() throws Exception {
        replicationService = new ReplicationService(config, eurekaRegistry, selfRegistrationService, peerAddressProvider, writeServerMetrics()) {
            @Override
            ReplicationChannel createRetryableSenderReplicationChannel(InetSocketAddress address) {
                lastAddedAddress = address;
                return replicationChannel;
            }
        };

        when(selfRegistrationService.resolve()).thenReturn(Observable.just(SELF_INFO));
    }

    @Test
    public void testConnectsToRemotePeers() throws Exception {
        when(peerAddressProvider.get()).thenReturn(Observable.just(new ChangeNotification<InetSocketAddress>(Kind.Add, ADDRESS)));

        // Connect to trigger replication process
        replicationService.connect();

        assertThat(lastAddedAddress, is(equalTo(ADDRESS)));
    }

    @Test
    public void testDisconnectsFromRemovedServers() throws Exception {
        when(peerAddressProvider.get()).thenReturn(Observable.just(
                new ChangeNotification<InetSocketAddress>(Kind.Add, ADDRESS),
                new ChangeNotification<InetSocketAddress>(Kind.Delete, ADDRESS)
        ));

        // Connect to trigger replication process
        replicationService.connect();

        verify(replicationChannel, times(1)).close();
    }
}