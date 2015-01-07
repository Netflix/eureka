package com.netflix.eureka2.server.channel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.server.bridge.InstanceInfoConverter;
import com.netflix.eureka2.server.bridge.InstanceInfoConverterImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.metric.BridgeChannelMetrics;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.*;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class BridgeChannelTest {

    @Mock
    private DiscoveryClient mockV1Client;

    @Mock
    private Applications mockApplications;

    private SourcedEurekaRegistry<InstanceInfo> registry;

    private int period = 5;
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
            registry = spy(new SourcedEurekaRegistryImpl(registryMetrics()));

            testScheduler = Schedulers.test();
            bridgeChannel = new BridgeChannel(registry, mockV1Client, period, SampleInstanceInfo.DiscoveryServer.build(), new BridgeChannelMetrics(), testScheduler);
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

    @Test
    public void testAddThenUpdate() {
        when(mockApplications.getRegisteredApplications())
                .thenReturn(Arrays.asList(app1t0))
                .thenReturn(Arrays.asList(app1t1));

        bridgeChannel.connect();
        testScheduler.advanceTimeTo(period - 1, TimeUnit.SECONDS);

        InstanceInfo app1t0Info = converter.fromV1(app1t0.getInstances().get(0));
        verify(registry, times(1)).register(app1t0Info);
        verify(registry, never()).unregister(any(InstanceInfo.class));

        testScheduler.advanceTimeTo(period * 2 - 1, TimeUnit.SECONDS);

        InstanceInfo app1t1Info = converter.fromV1(app1t1.getInstances().get(0));
        verify(registry, times(1)).register(app1t0Info);
        verify(registry, times(1)).register(app1t1Info);
        verify(registry, never()).unregister(any(InstanceInfo.class));
    }

    @Test
    public void testAddThenRemove() {
        when(mockApplications.getRegisteredApplications())
                .thenReturn(Arrays.asList(app2t0))
                .thenReturn(Arrays.asList(app2t1));

        bridgeChannel.connect();
        testScheduler.advanceTimeTo(period - 1, TimeUnit.SECONDS);

        InstanceInfo app2t0Info = converter.fromV1(app2t0.getInstances().get(0));
        verify(registry, times(1)).register(app2t0Info);
        verify(registry, never()).unregister(any(InstanceInfo.class));

        testScheduler.advanceTimeTo(period * 2 - 1, TimeUnit.SECONDS);

        verify(registry, times(1)).register(app2t0Info);
        verify(registry, times(1)).unregister(app2t0Info);
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
