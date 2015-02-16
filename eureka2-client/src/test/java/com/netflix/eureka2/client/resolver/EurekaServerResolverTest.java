package com.netflix.eureka2.client.resolver;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Set;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.utils.ExtCollections;
import com.netflix.eureka2.Server;
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

    private static final Interest<InstanceInfo> READ_SERVERS_INTEREST = Interests.forFullRegistry();
    private static final ServiceSelector EUREKA_SELECTOR =
            ServiceSelector.selectBy().serviceLabel(Names.DISCOVERY).protocolType(ProtocolType.IPv4);

    private static final Iterator<InstanceInfo> INSTANCE_INFO_IT =
            SampleInstanceInfo.collectionOf("resolver-test", SampleInstanceInfo.EurekaReadServer.build());
    private static final InstanceInfo INSTANCE_1 = INSTANCE_INFO_IT.next();
    private static final InstanceInfo INSTANCE_2 = INSTANCE_INFO_IT.next();

    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_1 = new ChangeNotification<>(ChangeNotification.Kind.Add, INSTANCE_1);
    private static final ChangeNotification<InstanceInfo> DELETE_INSTANCE_1 = new ChangeNotification<>(ChangeNotification.Kind.Delete, INSTANCE_1);
    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_2 = new ChangeNotification<>(ChangeNotification.Kind.Add, INSTANCE_2);
    private static final ChangeNotification<InstanceInfo> BUFFERING_SENTINEL = ChangeNotification.bufferSentinel();

    private final EurekaClientBuilder eurekaClientBuilder = mock(EurekaClientBuilder.class);
    private final EurekaClient eurekaClient = mock(EurekaClient.class);
    private final LoadBalancerBuilder<Server> loadBalancerBuilder = new DefaultLoadBalancerBuilder<>(null);

    private EurekaServerResolver eurekaServerResolver;

    @Before
    public void setUp() throws Exception {
        when(eurekaClientBuilder.build()).thenReturn(eurekaClient);
        eurekaServerResolver = new EurekaServerResolver(eurekaClientBuilder, READ_SERVERS_INTEREST, EUREKA_SELECTOR, loadBalancerBuilder);
    }

    @Test(timeout = 60000)
    public void testFetchesDataFromEurekaServer() throws Exception {
        // Returns two items in subscription
        when(eurekaClient.forInterest(READ_SERVERS_INTEREST)).thenReturn(Observable.just(ADD_INSTANCE_1, ADD_INSTANCE_2, BUFFERING_SENTINEL));

        // Resolve twice
        Server firstServer = eurekaServerResolver.resolve().toBlocking().firstOrDefault(null);
        Server secondServer = eurekaServerResolver.resolve().toBlocking().firstOrDefault(null);

        Set<Server> expected = ExtCollections.asSet(toServer(INSTANCE_1), toServer(INSTANCE_2));
        Set<Server> result = ExtCollections.asSet(firstServer, secondServer);
        assertThat(result, is(equalTo(expected)));
    }

    @Test(timeout = 60000)
    public void testRemovesStaleItems() throws Exception {
        // Batch with first item
        firstResolveWith(INSTANCE_1);

        // Delete instance1 and add instance 2.
        when(eurekaClient.forInterest(READ_SERVERS_INTEREST)).thenReturn(Observable.just(DELETE_INSTANCE_1, ADD_INSTANCE_2, ChangeNotification.<InstanceInfo>bufferSentinel()));

        assertResolvesTo(INSTANCE_2);
        assertResolvesTo(INSTANCE_2);
    }

    @Test(timeout = 60000)
    public void testFallsBackToStaleContentIfRefreshFails() throws Exception {
        // Batch with first item
        firstResolveWith(INSTANCE_1);

        // Send onError in the subscription stream
        Exception error = new Exception("channel error");
        when(eurekaClient.forInterest(READ_SERVERS_INTEREST)).thenReturn(Observable.<ChangeNotification<InstanceInfo>>error(error));

        assertResolvesTo(INSTANCE_1);
    }

    @Test(timeout = 60000)
    public void testReturnsErrorIfResolveFailedAndNoStaleEntryAvailable() throws Exception {
        Exception error = new Exception("channel error");
        when(eurekaClient.forInterest(READ_SERVERS_INTEREST)).thenReturn(Observable.<ChangeNotification<InstanceInfo>>error(error));
        assertResolveToError();
    }

    private void firstResolveWith(InstanceInfo instance) {
        when(eurekaClient.forInterest(READ_SERVERS_INTEREST)).thenReturn(
                Observable.just(
                        new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instance),
                        ChangeNotification.<InstanceInfo>bufferSentinel()
                )
        );
        assertResolvesTo(instance);
    }

    protected void assertResolvesTo(InstanceInfo instance) {
        Server server = eurekaServerResolver.resolve().toBlocking().firstOrDefault(null);
        assertThat(server, is(equalTo(toServer(instance))));
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