package com.netflix.eureka2.eureka1.bridge;

import java.util.concurrent.TimeUnit;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.bridge.config.Eureka1BridgeConfiguration;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.metric.server.BridgeServerMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistryRegistrationStub;
import com.netflix.eureka2.registry.Source.SourceMatcher;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.eureka1.bridge.config.bean.Eureka1BridgeConfigurationBean.anEureka1BridgeConfiguration;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xApplicationsFromV2Collection;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfo;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka2xInstanceInfo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Eureka1BridgeTest {

    private static final long REFRESH_INTERVAL = 30;

    private final TestScheduler testScheduler = Schedulers.test();

    private final EurekaRegistryRegistrationStub registry = spy(new EurekaRegistryRegistrationStub());

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
        InstanceInfo firstInstance = SampleInstanceInfo.WebServer.builder().build();
        InstanceInfo update = new InstanceInfo.Builder().withInstanceInfo(firstInstance).withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();
        when(discoveryClient.getApplications())
                .thenReturn(toEureka1xApplicationsFromV2Collection(firstInstance))
                .thenReturn(toEureka1xApplicationsFromV2Collection(update));
        when(registry.forSnapshot(any(Interest.class), any(SourceMatcher.class)))
                .thenReturn(Observable.<InstanceInfo>empty())
                // convert to v1 and then back to v2 to simulate the internal bridge conversions
                .thenReturn(Observable.just(toEureka2xInstanceInfo(toEureka1xInstanceInfo(firstInstance))));

        // Force first data reload
        testScheduler.advanceTimeTo(REFRESH_INTERVAL - 1, TimeUnit.SECONDS);
        testScheduler.triggerActions();

        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferStart
        ChangeNotification<InstanceInfo> notification = registry.getLatest(1, TimeUnit.SECONDS);
        assertThat(notification.isDataNotification(), is(true));
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Add));
        assertThat(notification.getData().getApp(), is(equalToIgnoringCase(firstInstance.getApp())));
        assertThat(notification.getData().getStatus(), is(firstInstance.getStatus()));
        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferEnd


        // Force second data reload
        testScheduler.advanceTimeTo(REFRESH_INTERVAL * 2 - 1, TimeUnit.SECONDS);

        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferStart
        notification = registry.getLatest(1, TimeUnit.SECONDS);
        assertThat(notification.isDataNotification(), is(true));
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Modify));
        assertThat(notification.getData().getApp(), is(equalToIgnoringCase(update.getApp())));
        assertThat(notification.getData().getStatus(), is(update.getStatus()));
        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferEnd
    }

    @Test
    public void testUnregisteredInstancesAreRemovedFromRegistry() throws Exception {
        InstanceInfo firstInstance = SampleInstanceInfo.WebServer.build();
        when(discoveryClient.getApplications()).thenReturn(toEureka1xApplicationsFromV2Collection(firstInstance));
        when(registry.forSnapshot(any(Interest.class), any(SourceMatcher.class))).thenReturn(Observable.<InstanceInfo>empty());

        // Force first data reload
        testScheduler.advanceTimeBy(REFRESH_INTERVAL - 1, TimeUnit.SECONDS);

        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferStart
        ChangeNotification<InstanceInfo> notification = registry.getLatest(1, TimeUnit.SECONDS);
        assertThat(notification.isDataNotification(), is(true));
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Add));
        assertThat(notification.getData().getApp(), is(equalToIgnoringCase(firstInstance.getApp())));
        assertThat(notification.getData().getStatus(), is(firstInstance.getStatus()));
        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferEnd

        // Now remove instance
        when(discoveryClient.getApplications()).thenReturn(new Applications());
        when(registry.forSnapshot(any(Interest.class), any(SourceMatcher.class))).thenReturn(Observable.just(firstInstance));

        // Force second data reload
        testScheduler.advanceTimeTo(REFRESH_INTERVAL * 2 - 1, TimeUnit.SECONDS);

        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferStart
        notification = registry.getLatest(1, TimeUnit.SECONDS);
        assertThat(notification.isDataNotification(), is(true));
        assertThat(notification.getKind(), is(ChangeNotification.Kind.Delete));
        assertThat(notification.getData().getApp(), is(equalToIgnoringCase(firstInstance.getApp())));
        assertThat(notification.getData().getStatus(), is(firstInstance.getStatus()));
        assertThat(registry.getLatest(1, TimeUnit.SECONDS).isStreamStateNotification(), is(true));  // bufferEnd
    }
}