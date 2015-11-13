package com.netflix.eureka2.eureka1.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.model.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.model.instance.StdServicePort;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.selector.ServiceSelector;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.*;
import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
@SuppressWarnings("unchecked")
public class Eureka1ModelConvertersTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    private final EurekaRegistry<InstanceInfo> registry = mock(EurekaRegistry.class);
    private final ReplaySubject<ChangeNotification<InstanceInfo>> notificationSubject = ReplaySubject.create();

    @Before
    public void setUp() throws Exception {
        when(registry.forInterest(any(Interest.class))).thenReturn(notificationSubject);
    }

    @Test
    public void testConversionToEureka1xDataCenterInfo() throws Exception {
        AwsDataCenterInfo v2DataCenterInfo = SampleAwsDataCenterInfo.UsEast1a.build();
        AmazonInfo v1DataCenterInfo = toEureka1xDataCenterInfo(v2DataCenterInfo);
        verifyDataCenterInfoMapping(v1DataCenterInfo, v2DataCenterInfo);
    }

    @Test
    public void testConversionToEureka1xInstanceInfo() {
        InstanceInfo v2InstanceInfo = SampleInstanceInfo.WebServer.build();
        com.netflix.appinfo.InstanceInfo v1InstanceInfo = toEureka1xInstanceInfo(v2InstanceInfo);

        assertThat(v1InstanceInfo.getAppGroupName(), is(equalToIgnoringCase(v2InstanceInfo.getAppGroup())));
        assertThat(v1InstanceInfo.getAppName(), is(equalToIgnoringCase(v2InstanceInfo.getApp())));
        assertThat(v1InstanceInfo.getASGName(), is(equalToIgnoringCase(v2InstanceInfo.getAsg())));
        assertThat(v1InstanceInfo.getVIPAddress(), is(equalToIgnoringCase(v2InstanceInfo.getVipAddress())));
        assertThat(v1InstanceInfo.getSecureVipAddress(), is(equalToIgnoringCase(v2InstanceInfo.getSecureVipAddress())));
        InstanceStatus mappedStatus = toEureka1xStatus(v2InstanceInfo.getStatus());
        assertThat(v1InstanceInfo.getStatus(), is(equalTo(mappedStatus)));

        // Data center info
        AwsDataCenterInfo dataCenterInfo = (AwsDataCenterInfo) v2InstanceInfo.getDataCenterInfo();
        verifyDataCenterInfoMapping((AmazonInfo) v1InstanceInfo.getDataCenterInfo(), (AwsDataCenterInfo) v2InstanceInfo.getDataCenterInfo());
        assertThat(v1InstanceInfo.getHostName(), is(equalToIgnoringCase(dataCenterInfo.getPublicAddress().getHostName())));

        // Network addresses
        assertThat(v1InstanceInfo.getHostName(), is(equalTo(dataCenterInfo.getPublicAddress().getHostName())));
        assertThat(v1InstanceInfo.getIPAddr(), is(equalTo(dataCenterInfo.getPublicAddress().getIpAddress())));

        // Port mapping
        int port = ServiceSelector.selectBy().secure(false).returnServiceEndpoint(v2InstanceInfo).getServicePort().getPort();
        int securePort = ServiceSelector.selectBy().secure(true).returnServiceEndpoint(v2InstanceInfo).getServicePort().getPort();
        assertThat(v1InstanceInfo.getPort(), is(equalTo(port)));
        assertThat(v1InstanceInfo.getSecurePort(), is(equalTo(securePort)));

        // Home/status/health check URLs
        assertThat(v1InstanceInfo.getHomePageUrl(), is(equalTo(v2InstanceInfo.getHomePageUrl())));
        assertThat(v1InstanceInfo.getStatusPageUrl(), is(equalTo(v2InstanceInfo.getStatusPageUrl())));
        assertThat(v1InstanceInfo.getHealthCheckUrls(), is(equalTo((Set<String>) v2InstanceInfo.getHealthCheckUrls())));

        // Lease info
        assertThat(v1InstanceInfo.getLeaseInfo(), is(notNullValue()));

        // Meta data
        assertThat(v1InstanceInfo.getMetadata(), is(equalTo(v2InstanceInfo.getMetaData())));
    }

    @Test
    public void testConversionToEureka2xDataCenterInfo() throws Exception {
        // First v2 -> v1
        AwsDataCenterInfo v2DataCenterInfo = SampleAwsDataCenterInfo.UsEast1a.build();
        AmazonInfo v1DataCenterInfo = toEureka1xDataCenterInfo(v2DataCenterInfo);

        // Now v1 -> v2, and check that resulting v2 record
        AwsDataCenterInfo mappedV2DataCenterInfo = (AwsDataCenterInfo) toEureka2xDataCenterInfo(v1DataCenterInfo);
        verifyDataCenterInfoMapping(v2DataCenterInfo, mappedV2DataCenterInfo);
    }

    @Test
    public void testConversionToEureka2xInstanceInfo() throws Exception {
        // First v2 -> v1
        InstanceInfo v2InstanceInfo = SampleInstanceInfo.WebServer.build();
        com.netflix.appinfo.InstanceInfo v1InstanceInfo = toEureka1xInstanceInfo(v2InstanceInfo);

        // Now v1 -> v2, and check that resulting v2 record
        InstanceInfo mappedV2InstanceInfo = toEureka2xInstanceInfo(v1InstanceInfo);

        AwsDataCenterInfo v2DataCenterInfo = (AwsDataCenterInfo) v2InstanceInfo.getDataCenterInfo();
        AwsDataCenterInfo mappedV2DataCenterInfo = (AwsDataCenterInfo) mappedV2InstanceInfo.getDataCenterInfo();

        assertThat(mappedV2InstanceInfo.getApp(), is(equalToIgnoringCase(v2InstanceInfo.getApp())));
        assertThat(mappedV2InstanceInfo.getAppGroup(), is(equalToIgnoringCase(v2InstanceInfo.getAppGroup())));
        assertThat(mappedV2InstanceInfo.getAsg(), is(equalToIgnoringCase(v2InstanceInfo.getAsg())));
        assertThat(mappedV2InstanceInfo.getHealthCheckUrls(), is(equalTo(v2InstanceInfo.getHealthCheckUrls())));
        assertThat(mappedV2InstanceInfo.getHomePageUrl(), is(equalToIgnoringCase(v2InstanceInfo.getHomePageUrl())));
        // v1 is computed from application name and AWS instance id (the original V2 id value is lost in the conversion)
        assertThat(mappedV2InstanceInfo.getId(), is(equalToIgnoringCase(v2InstanceInfo.getApp() + '_' + v2DataCenterInfo.getInstanceId())));
        assertThat(mappedV2InstanceInfo.getMetaData(), is(equalTo(v2InstanceInfo.getMetaData())));
        assertThat(mappedV2InstanceInfo.getSecureVipAddress(), is(equalTo(v2InstanceInfo.getSecureVipAddress())));
        assertThat(mappedV2InstanceInfo.getVipAddress(), is(equalTo(v2InstanceInfo.getVipAddress())));
        assertThat(mappedV2InstanceInfo.getStatus(), is(equalTo(v2InstanceInfo.getStatus())));
        assertThat(mappedV2InstanceInfo.getStatusPageUrl(), is(equalTo(v2InstanceInfo.getStatusPageUrl())));

        // Data center info
        verifyDataCenterInfoMapping(v2DataCenterInfo, mappedV2DataCenterInfo);
    }

    @Test
    public void testConversionToEureka2xInstanceInfoWithMetaEncodedServicePorts() throws Exception {
        // First v2 -> v1
        InstanceInfo v2InstanceInfo = SampleInstanceInfo.WebServer.build();
        com.netflix.appinfo.InstanceInfo v1InstanceInfo = toEureka1xInstanceInfo(v2InstanceInfo);
        v1InstanceInfo.getMetadata().put(EUREKA2_SERVICE_PORT_KEY_PREFIX + "0.name", "serviceA");
        v1InstanceInfo.getMetadata().put(EUREKA2_SERVICE_PORT_KEY_PREFIX + "0.port", "8888");

        // Now v1 -> v2, and check that resulting v2 record
        InstanceInfo mappedV2InstanceInfo = toEureka2xInstanceInfo(v1InstanceInfo);

        HashSet<ServicePort> ports = (HashSet<ServicePort>) mappedV2InstanceInfo.getPorts();
        assertThat(ports.size(), is(equalTo(1)));

        ServicePort port = ports.iterator().next();
        assertThat(port.getName(), is(equalTo("serviceA")));
    }

    @Test
    public void testConversionToEureka1xApplications() throws Exception {
        List<InstanceInfo> v2InstanceInfos = SampleInstanceInfo.WebServer.clusterOf(2);

        Applications applications = toEureka1xApplicationsFromV2Collection(v2InstanceInfos);

        assertThat(applications.getAppsHashCode(), is(notNullValue()));
        assertThat(applications.getRegisteredApplications().size(), is(equalTo(1)));
        assertThat(applications.getRegisteredApplications().get(0).getInstances().size(), is(equalTo(2)));
    }

    @Test
    public void testServicePortMappingToMetaData() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.WebServer.builder()
                .withPorts(
                        new StdServicePort("http", 80, false, asSet("private")),
                        new StdServicePort("https", 443, true, asSet("public", "private"))
                )
                .build();

        Map<String, String> servicePortMap = toServicePortMap(instanceInfo.getPorts());
        Collection<ServicePort> servicePorts = toServicePortSet(servicePortMap);

        assertThat(servicePorts, containsInAnyOrder(instanceInfo.getPorts().toArray()));
    }

    @Test
    public void testServicePortMapCleanup() throws Exception {
        Map<String, String> servicePortMap = new HashMap<>();
        servicePortMap.put("keyX", "valueX");
        servicePortMap.put(EUREKA2_SERVICE_PORT_KEY_PREFIX + "0.name", "serviceA");

        assertThat(removeServicePortMapEntries(servicePortMap), is(true));
        assertThat(servicePortMap.containsKey("keyX"), is(true));
        assertThat(removeServicePortMapEntries(servicePortMap), is(false));
    }

    private static void verifyDataCenterInfoMapping(AmazonInfo v1DataCenterInfo, AwsDataCenterInfo v2DataCenterInfo) {
        assertThat(v1DataCenterInfo.getName(), is(equalTo(Name.Amazon)));
        assertThat(v1DataCenterInfo.getId(), is(equalTo(v2DataCenterInfo.getInstanceId())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.amiId), is(equalTo(v2DataCenterInfo.getAmiId())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.availabilityZone), is(equalTo(v2DataCenterInfo.getZone())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.instanceId), is(equalTo(v2DataCenterInfo.getInstanceId())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.instanceType), is(equalTo(v2DataCenterInfo.getInstanceType())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.localIpv4), is(equalTo(v2DataCenterInfo.getPrivateAddress().getIpAddress())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.publicHostname), is(equalTo(v2DataCenterInfo.getPublicAddress().getHostName())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.publicIpv4), is(equalTo(v2DataCenterInfo.getPublicAddress().getIpAddress())));
    }

    private static void verifyDataCenterInfoMapping(AwsDataCenterInfo v2DataCenterInfo, AwsDataCenterInfo mappedV2DataCenterInfo) {
        // We check all meta info except placement group that is not present in v1
        assertThat(mappedV2DataCenterInfo.getAmiId(), is(equalTo(v2DataCenterInfo.getAmiId())));
        assertThat(mappedV2DataCenterInfo.getInstanceId(), is(equalTo(v2DataCenterInfo.getInstanceId())));
        assertThat(mappedV2DataCenterInfo.getInstanceType(), is(equalTo(v2DataCenterInfo.getInstanceType())));
        assertThat(mappedV2DataCenterInfo.getName(), is(equalTo(v2DataCenterInfo.getName())));
        assertThat(mappedV2DataCenterInfo.getRegion(), is(equalTo(v2DataCenterInfo.getRegion())));

        assertThat(mappedV2DataCenterInfo.getPublicAddress(), is(equalTo(v2DataCenterInfo.getPublicAddress())));
        assertThat(mappedV2DataCenterInfo.getPrivateAddress().getIpAddress(), is(equalTo(v2DataCenterInfo.getPrivateAddress().getIpAddress())));
    }
}