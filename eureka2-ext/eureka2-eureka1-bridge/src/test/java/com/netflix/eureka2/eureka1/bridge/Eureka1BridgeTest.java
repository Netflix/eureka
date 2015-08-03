package com.netflix.eureka2.eureka1.bridge;

import java.util.concurrent.TimeUnit;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.bridge.config.Eureka1BridgeConfiguration;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.SourceMatcher;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.eureka1.bridge.config.bean.Eureka1BridgeConfigurationBean.anEureka1BridgeConfiguration;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xApplicationsFromV2Collection;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Eureka1BridgeTest {

    private static final long REFRESH_INTERVAL = 30;

    private final TestScheduler testScheduler = Schedulers.test();

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);

    private final DiscoveryClient discoveryClient = mock(DiscoveryClient.class);

    private final Eureka1BridgeConfiguration config = anEureka1BridgeConfiguration().withRefreshRateSec(REFRESH_INTERVAL).build();

    private final Eureka1Bridge bridge = new Eureka1Bridge(
            registry, discoveryClient, config, BridgeServerMetricFactory.bridgeServerMetrics(), testScheduler
    );

    @Before
    public void setUp() throws Exception {
        bridge.start();
    }

    @After
    public void tearDown() throws Exception {
        bridge.stop();
    }

    @Test
    public void testNewInstancesAreAddedToRegistry() throws Exception {
        InstanceInfo firstInstance = SampleInstanceInfo.WebServer.build();
        when(discoveryClient.getApplications()).thenReturn(toEureka1xApplicationsFromV2Collection(firstInstance));
        when(registry.forSnapshot(any(Interest.class), any(SourceMatcher.class))).thenReturn(Observable.<InstanceInfo>empty());

        // Force first data reload
        testScheduler.triggerActions();

        ArgumentCaptor<InstanceInfo> instanceInfoCaptor = ArgumentCaptor.forClass(InstanceInfo.class);
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(registry, times(1)).register(instanceInfoCaptor.capture(), sourceCaptor.capture());

        assertThat(instanceInfoCaptor.getValue().getApp(), is(equalToIgnoringCase(firstInstance.getApp())));
    }

    @Test
    public void testUnregisteredInstancesAreRemovedFromRegistry() throws Exception {
        InstanceInfo firstInstance = SampleInstanceInfo.WebServer.build();
        when(discoveryClient.getApplications()).thenReturn(toEureka1xApplicationsFromV2Collection(firstInstance));
        when(registry.forSnapshot(any(Interest.class), any(SourceMatcher.class))).thenReturn(Observable.<InstanceInfo>empty());

        // Force first data reload
        testScheduler.triggerActions();

        // Now remove instance
        when(discoveryClient.getApplications()).thenReturn(new Applications());
        when(registry.forSnapshot(any(Interest.class), any(SourceMatcher.class))).thenReturn(Observable.just(firstInstance));

        testScheduler.advanceTimeBy(REFRESH_INTERVAL, TimeUnit.SECONDS);

        ArgumentCaptor<InstanceInfo> instanceInfoCaptor = ArgumentCaptor.forClass(InstanceInfo.class);
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(registry, times(1)).unregister(instanceInfoCaptor.capture(), sourceCaptor.capture());

        assertThat(instanceInfoCaptor.getValue().getApp(), is(equalToIgnoringCase(firstInstance.getApp())));
    }
}