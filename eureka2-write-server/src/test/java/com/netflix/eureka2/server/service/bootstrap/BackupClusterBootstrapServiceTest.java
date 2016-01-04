package com.netflix.eureka2.server.service.bootstrap;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.registry.index.IndexRegistryImpl;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolver;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class BackupClusterBootstrapServiceTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final ChangeNotification<InstanceInfo> INSTANCE_ADD_CHANGE = new ChangeNotification<>(Kind.Add, INSTANCE);
    private static final Source SOURCE = InstanceModel.getDefaultModel().createSource(Origin.BOOTSTRAP, "test");

    private static final Observable<ChangeNotification<InstanceInfo>> FOR_INTEREST_REPLY = Observable.just(
            StreamStateNotification.bufferStartNotification(Interests.forFullRegistry()),
            INSTANCE_ADD_CHANGE,
            StreamStateNotification.bufferEndNotification(Interests.forFullRegistry())
    );

    private final TestScheduler testScheduler = Schedulers.test();

    private final EurekaInterestClient snapshotInterestClient = mock(EurekaInterestClient.class);
    private final EurekaClusterResolver resolver = mock(EurekaClusterResolver.class);

    private RegistryBootstrapService bootstrapService;

    private final EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(
            new IndexRegistryImpl<InstanceInfo>(), EurekaRegistryMetricFactory.registryMetrics(), testScheduler);

    private final EurekaClientTransportFactory transportFactory = mock(EurekaClientTransportFactory.class);

    @Before
    public void setUp() throws Exception {
        ClusterAddress endpoint1 = ClusterAddress.readClusterAddressFrom("server1", 123);
        ClusterAddress endpoint2 = ClusterAddress.readClusterAddressFrom("server2", 123);
        when(resolver.clusterTopologyChanges()).thenReturn(
                Observable.just(
                        new ChangeNotification<ClusterAddress>(Kind.Add, endpoint1),
                        new ChangeNotification<ClusterAddress>(Kind.Add, endpoint2),
                        ChangeNotification.<ClusterAddress>bufferSentinel()
                )
        );

        bootstrapService = new BackupClusterBootstrapService(resolver, transportFactory) {
            @Override
            protected EurekaInterestClient createSnapshotInterestClient(Server server) {
                return snapshotInterestClient;
            }
        };
    }

    @Test(timeout = 30000)
    public void testBootstrapFromPeer() throws Exception {
        when(snapshotInterestClient.forInterest(any(Interest.class))).thenReturn(FOR_INTEREST_REPLY);

        bootstrapService.loadIntoRegistry(registry, SOURCE).toBlocking().firstOrDefault(null);
        testScheduler.triggerActions();

        assertThat(registry.size(), is(equalTo(1)));
    }

    @Test
    public void testRetryWithSubsequentPeerOnError() throws Exception {
        when(snapshotInterestClient.forInterest(any(Interest.class))).thenReturn(
                Observable.<ChangeNotification<InstanceInfo>>error(new Exception("Subscription error")),
                FOR_INTEREST_REPLY
        );

        bootstrapService.loadIntoRegistry(registry, SOURCE).toBlocking().firstOrDefault(null);
        testScheduler.triggerActions();

        assertThat(registry.size(), is(equalTo(1)));
    }

    @Test
    public void testRetryWithSubsequentPeerIfZeroItemsLoaded() throws Exception {
        when(snapshotInterestClient.forInterest(any(Interest.class))).thenReturn(
                Observable.<ChangeNotification<InstanceInfo>>empty(),
                FOR_INTEREST_REPLY
        );

        bootstrapService.loadIntoRegistry(registry, SOURCE).toBlocking().firstOrDefault(null);
        testScheduler.triggerActions();

        assertThat(registry.size(), is(equalTo(1)));
    }
}