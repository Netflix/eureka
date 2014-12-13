package com.netflix.eureka2.client.resolver;

import java.net.InetSocketAddress;
import java.util.Iterator;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.EurekaServerResolver.EurekaServerResolverBuilder;
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.InstanceInfo.Builder;
import com.netflix.eureka2.registry.InstanceInfo.Status;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import com.netflix.eureka2.registry.ServiceSelector;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.utils.Sets.asSet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EurekaServerResolverTest extends AbstractResolverTest {

    private static final ServiceSelector SELECTOR = ServiceSelector.selectBy().serviceLabel(Names.DISCOVERY).publicIp(true);

    private final InstanceInfo eurekaRead1;
    private final InstanceInfo eurekaRead2;
    private final Server eurekaServer1;
    private final Server eurekaServer2;
    private final String readClusterVip;

    private final EurekaClient eurekaClient = mock(EurekaClient.class);
    private final ReplaySubject<ChangeNotification<InstanceInfo>> notificationSubject = ReplaySubject.create();

    private EurekaServerResolver resolver;

    public EurekaServerResolverTest() {
        Iterator<InstanceInfo> readServers = SampleInstanceInfo.collectionOf("EurekaRead", SampleInstanceInfo.EurekaReadServer.build());
        this.eurekaRead1 = readServers.next();
        this.eurekaRead2 = readServers.next();
        this.eurekaServer1 = serverFrom(eurekaRead1);
        this.eurekaServer2 = serverFrom(eurekaRead2);
        this.readClusterVip = eurekaRead1.getVipAddress();
    }

    @Before
    public void setUp() throws Exception {
        when(eurekaClient.forVips(readClusterVip)).thenReturn(notificationSubject);

        resolver = new EurekaServerResolverBuilder()
                .withEurekaClient(eurekaClient)
                .withReadServerVip(readClusterVip)
                .build();
    }

    @Test
    public void testResolvesAddedAddresses() throws Exception {
        notificationSubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, eurekaRead1));
        assertThat(takeNext(resolver), is(equalTo(eurekaServer1)));
    }

    @Test
    public void testPurgesNotUpInstances() throws Exception {
        // Register two instance that are up
        notificationSubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, eurekaRead1));
        notificationSubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, eurekaRead2));

        assertThat(asSet(takeNext(resolver), takeNext(resolver)), is(equalTo(asSet(eurekaServer1, eurekaServer2))));

        // Take eurekaRead1 down
        InstanceInfo eureka1Down = new Builder().withInstanceInfo(eurekaRead1).withStatus(Status.DOWN).build();
        notificationSubject.onNext(new ModifyNotification<InstanceInfo>(eureka1Down, eureka1Down.diffOlder(eurekaRead1)));

        assertThat(asSet(takeNext(resolver), takeNext(resolver)), is(equalTo(asSet(eurekaServer2))));

        // Take eurekaRead1 up again
        notificationSubject.onNext(new ModifyNotification<InstanceInfo>(eurekaRead1, eurekaRead1.diffOlder(eureka1Down)));
        assertThat(asSet(takeNext(resolver), takeNext(resolver)), is(equalTo(asSet(eurekaServer1, eurekaServer2))));

        // And now remove eurekaRead1 completely
        notificationSubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Delete, eurekaRead1));
        assertThat(asSet(takeNext(resolver), takeNext(resolver)), is(equalTo(asSet(eurekaServer2))));
    }

    @Test
    public void testReleasesResourcesOnClose() throws Exception {
        // Warm up
        notificationSubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, eurekaRead1));
        takeNext(resolver);

        resolver.close();
        verify(eurekaClient, times(1)).close();
    }

    private static Server serverFrom(InstanceInfo instanceInfo) {
        InetSocketAddress address = SELECTOR.returnServiceAddress(instanceInfo);
        return new Server(address.getHostString(), address.getPort());
    }
}