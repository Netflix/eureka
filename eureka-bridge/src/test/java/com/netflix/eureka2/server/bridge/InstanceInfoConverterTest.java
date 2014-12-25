package com.netflix.eureka2.server.bridge;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author David Liu
 */
public class InstanceInfoConverterTest {

    private InstanceInfoConverter converter;
    private com.netflix.appinfo.InstanceInfo sourceV1Info;

    @Rule
    public final ExternalResource testResources = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            converter = new InstanceInfoConverterImpl();

            Map<String, String> metadata = new HashMap<>();
            metadata.put("enableRoute53", "true");
            metadata.put("route53RecordType", "CNAME");
            metadata.put("route53NamePrefix", "some-prefix");

            com.netflix.appinfo.DataCenterInfo dataCenterInfo = AmazonInfo.Builder.newBuilder()
                    .addMetadata(AmazonInfo.MetaDataKey.amiId, "amiId")
                    .addMetadata(AmazonInfo.MetaDataKey.instanceId, "instanceId")
                    .addMetadata(AmazonInfo.MetaDataKey.instanceType, "instanceType")
                    .addMetadata(AmazonInfo.MetaDataKey.localIpv4, "0.0.0.0")
                    .addMetadata(AmazonInfo.MetaDataKey.availabilityZone, "us-east-1a")
                    .addMetadata(AmazonInfo.MetaDataKey.publicHostname, "public-hostname")
                    .addMetadata(AmazonInfo.MetaDataKey.publicIpv4, "192.168.1.1")
                    .build();


            sourceV1Info = com.netflix.appinfo.InstanceInfo.Builder.newBuilder()
                    .setAppName("appName")
                    .setAppGroupName("appGroupName")
                    .setHostName("hostname")
                    .setStatus(com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE)
                    .setIPAddr("192.168.1.1")
                    .setPort(8080)
                    .setSecurePort(8043)
                    .setHomePageUrl("HomePage/relativeUrl", "HomePage/explicitUrl")
                    .setStatusPageUrl("StatusPage/relativeUrl", "StatusPage/explicitUrl")
                    .setHealthCheckUrls("HealthCheck/relativeUrl", "HealthCheck/explicitUrl", "HealthCheck/secureExplicitUrl")
                    .setVIPAddress("vipAddress")
                    .setASGName("asgName")
                    .setSecureVIPAddress("secureVipAddress")
                    .setMetadata(metadata)
                    .setDataCenterInfo(dataCenterInfo)
                    .build();
        }
    };

    @Test
    public void testV1ToV2() {
        InstanceInfo v2Info = converter.fromV1(sourceV1Info);

        assertThat(v2Info.getApp(), equalTo(sourceV1Info.getAppName()));
        assertThat(v2Info.getAppGroup(), equalTo(sourceV1Info.getAppGroupName()));
        assertThat(v2Info.getStatus().name(), equalTo(sourceV1Info.getStatus().name()));
        assertThat(v2Info.getPorts(), containsInAnyOrder(new ServicePort(sourceV1Info.getPort(), false), new ServicePort(sourceV1Info.getSecurePort(), true)));
        assertThat(v2Info.getHomePageUrl(), equalTo(sourceV1Info.getHomePageUrl()));
        assertThat(v2Info.getStatusPageUrl(), equalTo(sourceV1Info.getStatusPageUrl()));
        assertThat(v2Info.getHealthCheckUrls(), equalTo(sourceV1Info.getHealthCheckUrls()));

        assertThat(v2Info.getVipAddress(), equalTo(sourceV1Info.getVIPAddress()));
        assertThat(v2Info.getAsg(), equalTo(sourceV1Info.getASGName()));
        assertThat(v2Info.getSecureVipAddress(), equalTo(sourceV1Info.getSecureVipAddress()));

        // check datacenter info
        DataCenterInfo v2DataCenterInfo = v2Info.getDataCenterInfo();
        com.netflix.appinfo.DataCenterInfo v1DataCenterInfo = sourceV1Info.getDataCenterInfo();

        AwsDataCenterInfo v2 = (AwsDataCenterInfo) v2DataCenterInfo;
        AmazonInfo v1 = (AmazonInfo) v1DataCenterInfo;

        assertThat(v2.getAmiId(), equalTo(v1.get(AmazonInfo.MetaDataKey.amiId)));
        assertThat(v2.getInstanceId(), equalTo(v1.get(AmazonInfo.MetaDataKey.instanceId)));
        assertThat(v2.getInstanceType(), equalTo(v1.get(AmazonInfo.MetaDataKey.instanceType)));
        assertThat(v2.getZone(), equalTo(v1.get(AmazonInfo.MetaDataKey.availabilityZone)));
        assertThat(v2.getRegion(), equalTo("us-east-1"));

        assertThat(v2.getAddresses().size(), equalTo(2));
        assertThat(v2.getPublicAddress().getIpAddress(), equalTo(v1.get(AmazonInfo.MetaDataKey.publicIpv4)));
        assertThat(v2.getPublicAddress().getHostName(), equalTo(v1.get(AmazonInfo.MetaDataKey.publicHostname)));
        assertThat(v2.getPrivateAddress().getIpAddress(), equalTo(v1.get(AmazonInfo.MetaDataKey.localIpv4)));
    }

    @Test
    public void testV2ToV1() {
        // TODO
    }

}
