package com.netflix.eureka2.server.channel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.channel.BridgeChannel;
import com.netflix.eureka2.channel.BridgeChannel.STATE;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.noop.NoOpEurekaRegistryMetrics;
import com.netflix.eureka2.metric.noop.NoOpSerializedTaskInvokerMetrics;
import com.netflix.eureka2.metric.server.BridgeChannelMetrics;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.bridge.InstanceInfoConverter;
import com.netflix.eureka2.server.bridge.InstanceInfoConverterImpl;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class BridgeChannelTest {

    private static final int PERIOD = 5;

    @Mock
    private EurekaRegistryMetricFactory registryMetrics;

    @Mock
    private BridgeChannelMetrics channelMetrics;

    @Mock
    private DiscoveryClient mockV1Client;

    @Mock
    private Applications mockApplications;

    private SourcedEurekaRegistry<InstanceInfo> registry;

    private TestScheduler testScheduler;
    private BridgeChannel bridgeChannel;

    private InstanceInfoConverter converter;

    private Application app1t0;
    private Application app1t1;

    private Application app2t0;
    private Application app2t1;

    @Rule
    public final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            when(registryMetrics.getRegistryTaskInvokerMetrics()).thenReturn(NoOpSerializedTaskInvokerMetrics.INSTANCE);
            when(registryMetrics.getEurekaServerRegistryMetrics()).thenReturn(NoOpEurekaRegistryMetrics.INSTANCE);

            registry = spy(new SourcedEurekaRegistryImpl(registryMetrics));

            testScheduler = Schedulers.test();
            bridgeChannel = new BridgeChannelImpl(
                    registry,
                    mockV1Client,
                    PERIOD,
                    SampleInstanceInfo.DiscoveryServer.build(),
                    channelMetrics,
                    testScheduler
            );
            converter = new InstanceInfoConverterImpl();

            when(mockV1Client.getApplications()).thenReturn(mockApplications);

            // app1 updates
            app1t0 = new Application("app1");
            app1t0.addInstance(buildV1InstanceInfo("1-id", "app1", com.netflix.appinfo.InstanceInfo.InstanceStatus.STARTING));

            app1t1 = new Application("app1");
            app1t1.addInstance(buildV1InstanceInfo("1-id", "app1", com.netflix.appinfo.InstanceInfo.InstanceStatus.UP));

            // app2 removes
            app2t0 = new Application("app2");
            app2t0.addInstance(buildV1InstanceInfo("2-id", "app2", com.netflix.appinfo.InstanceInfo.InstanceStatus.UP));

            app2t1 = new Application("app2");
        }
    };

    @Test(timeout = 60000)
    public void testAddThenUpdate() {
        when(mockApplications.getRegisteredApplications())
                .thenReturn(Arrays.asList(app1t0))
                .thenReturn(Arrays.asList(app1t1));

        bridgeChannel.connect();
        testScheduler.advanceTimeTo(PERIOD - 1, TimeUnit.SECONDS);

        InstanceInfo app1t0Info = converter.fromV1(app1t0.getInstances().get(0));
        verify(registry, times(1)).register(app1t0Info, bridgeChannel.getSource());
        verify(registry, never()).unregister(any(InstanceInfo.class), eq(bridgeChannel.getSource()));

        testScheduler.advanceTimeTo(PERIOD * 2 - 1, TimeUnit.SECONDS);

        InstanceInfo app1t1Info = converter.fromV1(app1t1.getInstances().get(0));
        verify(registry, times(1)).register(app1t0Info, bridgeChannel.getSource());
        verify(registry, times(1)).register(app1t1Info, bridgeChannel.getSource());
        verify(registry, never()).unregister(any(InstanceInfo.class), eq(bridgeChannel.getSource()));
    }

    @Test(timeout = 60000)
    public void testAddThenRemove() {
        when(mockApplications.getRegisteredApplications())
                .thenReturn(Arrays.asList(app2t0))
                .thenReturn(Arrays.asList(app2t1));

        bridgeChannel.connect();
        testScheduler.advanceTimeTo(PERIOD - 1, TimeUnit.SECONDS);

        InstanceInfo app2t0Info = converter.fromV1(app2t0.getInstances().get(0));
        verify(registry, times(1)).register(app2t0Info, bridgeChannel.getSource());
        verify(registry, never()).unregister(any(InstanceInfo.class), eq(bridgeChannel.getSource()));

        testScheduler.advanceTimeTo(PERIOD * 2 - 1, TimeUnit.SECONDS);

        verify(registry, times(1)).register(app2t0Info, bridgeChannel.getSource());
        verify(registry, times(1)).unregister(app2t0Info, bridgeChannel.getSource());
    }

    @Test(timeout = 60000)
    public void testMetrics() throws Exception {
        when(mockApplications.getRegisteredApplications())
                .thenReturn(Arrays.asList(app1t0))
                .thenReturn(Arrays.asList(app1t1))
                .thenReturn(Arrays.asList(app2t1));

        // Connect moves to STATE.Opened
        bridgeChannel.connect();
        verify(channelMetrics, times(1)).incrementStateCounter(STATE.Opened);

        // Replicate one item. We do it twice to simulate update
        testScheduler.advanceTimeBy(PERIOD, TimeUnit.SECONDS);
        verify(channelMetrics, times(1)).setRegisterCount(1);
        verify(channelMetrics, times(1)).setUpdateCount(1);
        verify(channelMetrics, times(2)).setTotalCount(1);
        reset(channelMetrics);

        // Last update has no apps
        testScheduler.advanceTimeBy(PERIOD, TimeUnit.SECONDS);
        verify(channelMetrics, times(1)).setRegisterCount(0);
        verify(channelMetrics, times(1)).setUnregisterCount(1);
        verify(channelMetrics, times(1)).setTotalCount(0);
    }

    private static com.netflix.appinfo.InstanceInfo buildV1InstanceInfo(String id, String appName, com.netflix.appinfo.InstanceInfo.InstanceStatus status) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("enableRoute53", "true");
        metadata.put("route53RecordType", "CNAME");
        metadata.put("route53NamePrefix", "some-prefix");

        com.netflix.appinfo.DataCenterInfo dataCenterInfo = AmazonInfo.Builder.newBuilder()
                .addMetadata(AmazonInfo.MetaDataKey.amiId, "amiId")
                .addMetadata(AmazonInfo.MetaDataKey.instanceId, id)
                .addMetadata(AmazonInfo.MetaDataKey.instanceType, "instanceType")
                .addMetadata(AmazonInfo.MetaDataKey.localIpv4, "0.0.0.0")
                .addMetadata(AmazonInfo.MetaDataKey.availabilityZone, "us-east-1a")
                .addMetadata(AmazonInfo.MetaDataKey.publicHostname, "public-hostname")
                .addMetadata(AmazonInfo.MetaDataKey.publicIpv4, "192.168.1.1")
                .build();

        return com.netflix.appinfo.InstanceInfo.Builder.newBuilder()
                .setAppName(appName)
                .setAppGroupName(appName + "#group")
                .setHostName(appName + "#hostname")
                .setStatus(status)
                .setIPAddr(appName + "#ip")
                .setPort(8080)
                .setSecurePort(8043)
                .setHomePageUrl("HomePage/relativeUrl", "HomePage/explicitUrl")
                .setStatusPageUrl("StatusPage/relativeUrl", "StatusPage/explicitUrl")
                .setHealthCheckUrls("HealthCheck/relativeUrl", "HealthCheck/explicitUrl", "HealthCheck/secureExplicitUrl")
                .setVIPAddress(appName + "#vipAddress")
                .setASGName(appName + "#asgName")
                .setSecureVIPAddress(appName + "#secureVipAddress")
                .setMetadata(metadata)
                .setDataCenterInfo(dataCenterInfo)
                .build();
    }
}
