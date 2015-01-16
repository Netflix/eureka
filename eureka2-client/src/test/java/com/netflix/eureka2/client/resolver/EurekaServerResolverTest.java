package com.netflix.eureka2.client.resolver;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Set;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.channel.SnapshotInterestChannel;
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.utils.Sets;
import netflix.ocelli.LoadBalancerBuilder;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Notification.Kind;
import rx.Observable;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EurekaServerResolverTest {

    private static final Interest<InstanceInfo> SNAPSHOT_INTEREST = Interests.forFullRegistry();
    private static final ServiceSelector EUREKA_SELECTOR =
            ServiceSelector.selectBy().serviceLabel(Names.DISCOVERY).protocolType(ProtocolType.IPv4);

    private static final Iterator<InstanceInfo> INSTANCE_INFO_IT =
            SampleInstanceInfo.collectionOf("resolver-test", SampleInstanceInfo.EurekaReadServer.build());
    private static final InstanceInfo INSTANCE_1 = INSTANCE_INFO_IT.next();
    private static final InstanceInfo INSTANCE_2 = INSTANCE_INFO_IT.next();

    private final SnapshotInterestChannel snapshotInterestChannel = mock(SnapshotInterestChannel.class);
    private final LoadBalancerBuilder<Server> loadBalancerBuilder = new DefaultLoadBalancerBuilder<>(null);

    private EurekaServerResolver eurekaServerResolver;

    @Before
    public void setUp() throws Exception {
        eurekaServerResolver = new EurekaServerResolver(snapshotInterestChannel, SNAPSHOT_INTEREST, EUREKA_SELECTOR, loadBalancerBuilder);
    }

    @Test
    public void testFetchesSnapshotFromEurekaServer() throws Exception {
        // Inject two item snapshot
        when(snapshotInterestChannel.forSnapshot(SNAPSHOT_INTEREST)).thenReturn(Observable.just(INSTANCE_1, INSTANCE_2));

        // Resolve twice
        Server firstServer = eurekaServerResolver.resolve().toBlocking().firstOrDefault(null);
        Server secondServer = eurekaServerResolver.resolve().toBlocking().firstOrDefault(null);

        Set<Server> expected = Sets.asSet(toServer(INSTANCE_1), toServer(INSTANCE_2));
        Set<Server> result = Sets.asSet(firstServer, secondServer);
        assertThat(result, is(equalTo(expected)));
    }

    @Test
    public void testRemovesStaleItems() throws Exception {
        // Snapshot with first item
        firstResolveWith(INSTANCE_1);

        // Snapshot with a new, second item only
        when(snapshotInterestChannel.forSnapshot(SNAPSHOT_INTEREST)).thenReturn(Observable.just(INSTANCE_2));

        assertResolvesTo(INSTANCE_2);
        assertResolvesTo(INSTANCE_2);
    }

    @Test
    public void testFallsBackToStaleContentIfRefreshFails() throws Exception {
        // Snapshot with first item
        firstResolveWith(INSTANCE_1);

        // Snapshot that completes with onError
        Exception error = new Exception("channel error");
        when(snapshotInterestChannel.forSnapshot(SNAPSHOT_INTEREST)).thenReturn(Observable.<InstanceInfo>error(error));

        assertResolvesTo(INSTANCE_1);
    }

    @Test
    public void testReturnsErrorIfResolveFailedAndNoStaleEntryAvailable() throws Exception {
        Exception error = new Exception("channel error");
        when(snapshotInterestChannel.forSnapshot(SNAPSHOT_INTEREST)).thenReturn(Observable.<InstanceInfo>error(error));
        assertResolveToError();
    }

    @Test
    public void testFallsBackToStaleContentIfEmptyListReturned() throws Exception {
        // Snapshot with first item
        firstResolveWith(INSTANCE_1);

        // Snapshot that completes with onError
        when(snapshotInterestChannel.forSnapshot(SNAPSHOT_INTEREST)).thenReturn(Observable.<InstanceInfo>empty());
        assertResolvesTo(INSTANCE_1);
    }

    private void firstResolveWith(InstanceInfo instance) {
        when(snapshotInterestChannel.forSnapshot(SNAPSHOT_INTEREST)).thenReturn(Observable.just(instance));
        assertResolvesTo(instance);
    }

    protected void assertResolvesTo(InstanceInfo instance2) {
        Server server = eurekaServerResolver.resolve().toBlocking().firstOrDefault(null);
        assertThat(server, is(equalTo(toServer(instance2))));
    }

    private void assertResolveToError() {
        Notification<Server> notification = eurekaServerResolver.resolve().materialize().toBlocking().first();
        assertThat(notification.getKind(), is(equalTo(Kind.OnError)));
        // the error we get back is an ocelli error, so no need to check it's specific type (at least until ocelli is more stable)
    }

    private static Server toServer(InstanceInfo instance) {
        InetSocketAddress address = EUREKA_SELECTOR.returnServiceAddress(instance);
        return new Server(address.getHostString(), address.getPort());
    }
}