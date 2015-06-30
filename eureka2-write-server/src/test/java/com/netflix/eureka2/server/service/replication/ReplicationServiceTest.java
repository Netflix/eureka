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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.metric.server.WriteServerMetricFactory.writeServerMetrics;
import static com.netflix.eureka2.server.config.bean.WriteServerConfigBean.aWriteServerConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class ReplicationServiceTest {

    private static final InstanceInfo SELF_INFO = SampleInstanceInfo.DiscoveryServer.build();

    private static final Server ADDRESS1 = new Server("host1", 123);
    private static final Server ADDRESS2 = new Server("host2", 456);
    private static final Server ADDRESS3 = new Server("host3", 789);

    private final WriteServerConfig config = aWriteServerConfig().build();
    private final SourcedEurekaRegistry<InstanceInfo> eurekaRegistry = mock(SourcedEurekaRegistry.class);
    private final SelfInfoResolver selfIdentityService = mock(SelfInfoResolver.class);
    private final ReplicationPeerAddressesProvider peerAddressProvider = mock(ReplicationPeerAddressesProvider.class);

    private final ReplaySubject<ChangeNotification<Server>> peerAddressSubject = ReplaySubject.create();

    private ReplicationService replicationService;

    @Before
    public void setUp() throws Exception {
        replicationService = new ReplicationService(config, eurekaRegistry, selfIdentityService, peerAddressProvider, writeServerMetrics());
        when(selfIdentityService.resolve()).thenReturn(Observable.just(SELF_INFO));
        when(peerAddressProvider.get()).thenReturn(peerAddressSubject.asObservable());
    }

    @Test(timeout = 60000)
    public void testConnectsToRemotePeers() throws Exception {
        Map<Server, ReplicationSender> addressVsHandler = replicationService.addressVsHandler;
        assertThat(addressVsHandler.size(), is(0));

        // Connect to trigger replication process
        replicationService.connect();
        assertThat(addressVsHandler.size(), is(0));

        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS1));
        assertThat(addressVsHandler.size(), is(1));
        assertThat(addressVsHandler.keySet().iterator().next(), is(equalTo(ADDRESS1)));
    }

    @Test(timeout = 60000)
    public void testDisconnectsFromRemovedServers() throws Exception {
        Map<Server, ReplicationSender> addressVsHandler = replicationService.addressVsHandler;
        assertThat(addressVsHandler.size(), is(0));

        // Connect to trigger replication process
        replicationService.connect();
        assertThat(addressVsHandler.size(), is(0));

        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS1));
        assertThat(addressVsHandler.size(), is(1));
        Map.Entry<Server, ReplicationSender> entry = addressVsHandler.entrySet().iterator().next();
        assertThat(entry.getKey(), is(equalTo(ADDRESS1)));

        // hotswap the handler with a spy of itself so we can check shutdown
        ReplicationSender spyHandler = spy(entry.getValue());
        addressVsHandler.put(entry.getKey(), spyHandler);

        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Delete, ADDRESS1));
        assertThat(addressVsHandler.size(), is(0));

        verify(spyHandler, times(1)).shutdown();
    }

    @Test(timeout = 60000)
    public void testShutdownCleanUpResources() {
        Map<Server, ReplicationSender> addressVsHandler = replicationService.addressVsHandler;
        assertThat(addressVsHandler.size(), is(0));

        // Connect to trigger replication process
        replicationService.connect();
        assertThat(addressVsHandler.size(), is(0));

        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS1));
        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS2));
        assertThat(addressVsHandler.size(), is(2));

        // hotswap all of the handlers with spies so we can check shutdown
        List<ReplicationSender> spies = new ArrayList<>();
        for (Map.Entry<Server, ReplicationSender> entry : replicationService.addressVsHandler.entrySet()) {
            ReplicationSender spyHandler = spy(entry.getValue());
            spies.add(spyHandler);
            addressVsHandler.put(entry.getKey(), spyHandler);
        }

        replicationService.close();

        assertThat(addressVsHandler.size(), is(0));
        for (ReplicationSender spyHandler : spies) {
            verify(spyHandler, times(1)).shutdown();
        }

        // try to send more servers after close and verify they are not added to the map
        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS3));
        assertThat(addressVsHandler.size(), is(0));
    }
}