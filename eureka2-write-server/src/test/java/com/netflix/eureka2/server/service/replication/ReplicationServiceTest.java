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

import java.util.Map;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.channel.ReplicationHandlerStub;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.metric.server.WriteServerMetricFactory.writeServerMetrics;
import static com.netflix.eureka2.server.config.bean.WriteServerConfigBean.aWriteServerConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class ReplicationServiceTest {

    private static final InstanceInfo SELF_INFO = SampleInstanceInfo.DiscoveryServer.build();

    private static final Server ADDRESS1 = new Server("host1", 123);
    private static final Server ADDRESS2 = new Server("host2", 456);

    private final WriteServerConfig config = aWriteServerConfig().build();

    private final EurekaRegistry<InstanceInfo> eurekaRegistry = mock(EurekaRegistry.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> localForInterest = PublishSubject.create();

    private final SelfInfoResolver selfIdentityService = mock(SelfInfoResolver.class);
    private final ReplicationPeerAddressesProvider peerAddressProvider = mock(ReplicationPeerAddressesProvider.class);
    private final EurekaClientTransportFactory transportFactory = mock(EurekaClientTransportFactory.class);

    private final ReplaySubject<ChangeNotification<Server>> peerAddressSubject = ReplaySubject.create();

    private ReplicationService replicationService;

    @Before
    public void setUp() throws Exception {
        replicationService = new ReplicationService(config, eurekaRegistry, selfIdentityService, peerAddressProvider, writeServerMetrics(), transportFactory);
        when(selfIdentityService.resolve()).thenReturn(Observable.just(SELF_INFO));
        when(peerAddressProvider.get()).thenReturn(peerAddressSubject.asObservable());
        when(eurekaRegistry.forInterest(any(), any())).thenReturn(localForInterest);
        when(transportFactory.newReplicationTransport(any())).thenAnswer(invocation -> {
            Server server = (Server) invocation.getArguments()[0];
            return new ReplicationHandlerStub(InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, server.getHost()));
        });
    }

    @Test(timeout = 60000)
    public void testConnectsToRemotePeers() throws Exception {
        Map<Server, Subscription> addressVsHandler = replicationService.addressVsPipelineSubscription;
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
        Map<Server, Subscription> addressVsHandler = replicationService.addressVsPipelineSubscription;
        assertThat(addressVsHandler.size(), is(0));

        // Connect to trigger replication process
        replicationService.connect();
        assertThat(addressVsHandler.size(), is(0));

        // Add server1
        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS1));
        assertThat(addressVsHandler.size(), is(1));
        Map.Entry<Server, Subscription> entry = addressVsHandler.entrySet().iterator().next();
        assertThat(entry.getKey(), is(equalTo(ADDRESS1)));

        // Remove server1
        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Delete, ADDRESS1));
        assertThat(addressVsHandler.size(), is(0));
    }

    @Test(timeout = 60000)
    public void testShutdownCleanUpResources() {
        Map<Server, Subscription> addressVsHandler = replicationService.addressVsPipelineSubscription;
        assertThat(addressVsHandler.size(), is(0));

        // Connect to trigger replication process
        replicationService.connect();
        assertThat(addressVsHandler.size(), is(0));

        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS1));
        peerAddressSubject.onNext(new ChangeNotification<>(Kind.Add, ADDRESS2));
        assertThat(addressVsHandler.size(), is(2));

        replicationService.close();
        assertThat(addressVsHandler.size(), is(0));
    }
}