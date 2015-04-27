package com.netflix.eureka2.server.service.bootstrap;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.resolver.EurekaEndpoint;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolver;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class PeerRegistryBootstrapServiceTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final ChangeNotification<InstanceInfo> INSTANCE_ADD_CHANGE = new ChangeNotification<>(Kind.Add, INSTANCE);
    private static final Source SOURCE = new Source(Origin.BOOTSTRAP, "test");

    private final LightEurekaInterestClient lightEurekaInterestClient = mock(LightEurekaInterestClient.class);
    private final EurekaEndpointResolver resolver = mock(EurekaEndpointResolver.class);

    private RegistryBootstrapService bootstrapService;

    private final SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics());

    @Before
    public void setUp() throws Exception {
        // Peers stream does not complete, to test that we terminate the subscription once first
        // peer provides registry data.
        EurekaEndpoint endpoint = EurekaEndpoint.readServerEndpointFrom("localhost", 123);
        when(resolver.eurekaEndpoints()).thenReturn(
                Observable.just(
                        new ChangeNotification<EurekaEndpoint>(Kind.Add, endpoint),
                        ChangeNotification.<EurekaEndpoint>bufferSentinel()
                )
        );
        Observable<ChangeNotification<InstanceInfo>> forInterestReply = Observable.just(
                StreamStateNotification.bufferStartNotification(Interests.forFullRegistry()),
                INSTANCE_ADD_CHANGE,
                StreamStateNotification.bufferEndNotification(Interests.forFullRegistry())
        );
        when(lightEurekaInterestClient.forInterest(any(Interest.class))).thenReturn(forInterestReply);

        bootstrapService = new PeerRegistryBootstrapService(resolver, Schedulers.computation()) {
            @Override
            protected LightEurekaInterestClient createLightEurekaInterestClient(Server server) {
                return lightEurekaInterestClient;
            }
        };
    }

    @Test(timeout = 30000)
    public void testBootstrapFromPeer() throws Exception {
        bootstrapService.loadIntoRegistry(registry, SOURCE).toBlocking().firstOrDefault(null);

        assertThat(registry.size(), is(equalTo(1)));
    }
}