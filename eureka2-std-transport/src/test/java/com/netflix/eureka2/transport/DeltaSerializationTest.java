package com.netflix.eureka2.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.netflix.eureka2.codec.jackson.JacksonEurekaCodecFactory;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.StdInstanceModel;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.instance.InstanceInfoField;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.model.instance.StdDelta;
import com.netflix.eureka2.model.instance.StdDelta.Builder;
import com.netflix.eureka2.model.instance.StdServicePort;
import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DeltaSerializationTest {

    static {
        InstanceModel.setDefaultModel(StdInstanceModel.getStdModel());
    }

    private final EurekaCodec eurekaCodec = new JacksonEurekaCodecFactory(StdDelta.class).getCodec();
    private final DeltaBuilder deltaBuilder = new Builder().withId("id1");

    @Test
    public void testDeltaSerialization_WithAppGroup() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.APPLICATION_GROUP, "appGroup1").build());
    }

    @Test
    public void testDeltaSerialization_WithApp() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.APPLICATION, "app1").build());
    }

    @Test
    public void testDeltaSerialization_WithAsg() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.ASG, "asg1").build());
    }

    @Test
    public void testDeltaSerialization_WithVipAddress() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.VIP_ADDRESS, "vip1").build());
    }

    @Test
    public void testDeltaSerialization_WithSecureVipAddress() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.SECURE_VIP_ADDRESS, "secureVip1").build());
    }

    @Test
    public void testDeltaSerialization_WithPorts() throws Exception {
        HashSet<ServicePort> ports = new HashSet<>();
        ports.add(new StdServicePort(7001, false));
        ports.add(new StdServicePort(7002, true));
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.PORTS, ports).build());
    }

    @Test
    public void testDeltaSerialization_WithStatus() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.STATUS, Status.STARTING).build());
    }

    @Test
    public void testDeltaSerialization_WithHomePageUrl() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.HOMEPAGE_URL, "http://homepage").build());
    }

    @Test
    public void testDeltaSerialization_WithStatusPageUrl() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.STATUS_PAGE_URL, "http://status").build());
    }

    @Test
    public void testDeltaSerialization_WithHealthCheckUrl() throws Exception {
        HashSet<String> healthcheckUrls = new HashSet<>();
        healthcheckUrls.add("http://healthcheck1");
        healthcheckUrls.add("http://healthcheck2");
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.HEALTHCHECK_URLS, healthcheckUrls).build());
    }

    @Test
    public void testDeltaSerialization_WithMetaInfo() throws Exception {
        Map<String, String> healthcheckUrls = new HashMap<>();
        healthcheckUrls.put("key1", "value1");
        healthcheckUrls.put("key2", "value2");
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.META_DATA, healthcheckUrls).build());
    }

    @Test
    public void testDeltaSerialization_WithDataCenterInfo() throws Exception {
        verifyEncoding(deltaBuilder.withDelta(InstanceInfoField.DATA_CENTER_INFO, SampleAwsDataCenterInfo.UsEast1a.build()).build());
    }

    private void verifyEncoding(Delta<?> delta) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        eurekaCodec.encode(delta, output);
        byte[] encoded = output.toByteArray();
        System.out.println(new String(encoded));
        Delta<?> decoded = eurekaCodec.decode(new ByteArrayInputStream(encoded), StdDelta.class);

        assertThat(decoded, is(equalTo(delta)));
    }
}
