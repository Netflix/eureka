package com.netflix.eureka2.client.resolver;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Set;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.Server;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Notification;
import rx.Notification.Kind;
import rx.Observable;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EurekaServerResolverTest extends AbstractResolverTest {

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

    private final EurekaInterestClientBuilder interestClientBuilder = mock(EurekaInterestClientBuilder.class);
    private final EurekaInterestClient interestClient = mock(EurekaInterestClient.class);

    private volatile ServerResolver eurekaServerResolver;

    @Before
    public void setUp() {
        when(interestClientBuilder.build()).thenReturn(interestClient);
    }

    @Test(timeout = 30000)
    public void testFetchesDataFromEurekaServer() throws Exception {
        // Returns two items in subscription
        when(interestClient.forInterest(READ_SERVERS_INTEREST)).thenReturn(Observable.just(ADD_INSTANCE_1, ADD_INSTANCE_2, BUFFERING_SENTINEL));
        eurekaServerResolver = new DefaultEurekaResolverStep(interestClientBuilder).forInterest(READ_SERVERS_INTEREST);

        // Resolve three times, should be overlap
        Set<Server> actual = asSet(takeNext(eurekaServerResolver), takeNext(eurekaServerResolver), takeNext(eurekaServerResolver));

        Set<Server> expected = asSet(toServer(INSTANCE_1), toServer(INSTANCE_2));
        assertThat(actual, is(equalTo(expected)));
    }

    @Test(timeout = 30000)
    public void testFallsBackToStaleContentIfRefreshFails() throws Exception {
        final Exception error = new Exception("test error");

        when(interestClient.forInterest(READ_SERVERS_INTEREST))
                .thenAnswer(new Answer<Observable<ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<ChangeNotification<InstanceInfo>> answer(InvocationOnMock invocation) throws Throwable {
                        return  Observable.just(
                                new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, INSTANCE_1),
                                ChangeNotification.<InstanceInfo>bufferSentinel()
                        );
                    }
                })
                .thenAnswer(new Answer<Observable<ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<ChangeNotification<InstanceInfo>> answer(InvocationOnMock invocation) throws Throwable {
                        return Observable.error(error);
                    }
                });

        eurekaServerResolver = new DefaultEurekaResolverStep(interestClientBuilder).forInterest(READ_SERVERS_INTEREST);

        // Batch with first item
        assertResolvesTo(INSTANCE_1);

        // try again, should emit error but still resolve to instance1 due to loadbalancer
        assertResolvesTo(INSTANCE_1);
    }

    @Test(timeout = 30000)
    public void testReturnsErrorIfResolveFailedAndNoStaleEntryAvailable() throws Exception {
        Exception error = new Exception("channel error");
        when(interestClient.forInterest(READ_SERVERS_INTEREST)).thenReturn(Observable.<ChangeNotification<InstanceInfo>>error(error));
        eurekaServerResolver = new DefaultEurekaResolverStep(interestClientBuilder).forInterest(READ_SERVERS_INTEREST);
        assertResolveToError();
    }

    protected void assertResolvesTo(InstanceInfo instance) {
        Server server = takeNext(eurekaServerResolver);
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