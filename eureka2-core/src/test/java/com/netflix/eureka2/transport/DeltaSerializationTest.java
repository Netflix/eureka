package com.netflix.eureka2.transport;

import com.netflix.eureka2.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfoField;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import com.netflix.eureka2.transport.codec.avro.AvroCodec;
import com.netflix.eureka2.transport.utils.AvroUtils;
import com.netflix.eureka2.utils.Sets;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.avro.Schema;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author David Liu
 */
public class DeltaSerializationTest {

    private final InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
    private EmbeddedChannel channel;

    @Before
    public void setup() {
        Schema schema = AvroUtils.loadSchema(EurekaTransports.DISCOVERY_SCHEMA_FILE, EurekaTransports.DISCOVERY_ENVELOPE_TYPE);
        AvroCodec avroCodec = new AvroCodec(EurekaTransports.DISCOVERY_PROTOCOL_MODEL_SET, schema);
        channel = new EmbeddedChannel(avroCodec);
    }

    @Test(timeout = 60000)
    public void testDeltaSerializationWithAvro_HashSetInt() throws Exception {
        HashSet<ServicePort> newPorts = SampleServicePort.httpPorts();
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withDelta(InstanceInfoField.PORTS, newPorts)
                .build();

        doDeltaTest(delta);
    }

    @Test(timeout = 60000)
    public void testDeltaSerializationWithAvro_HashSetString() throws Exception {
        HashSet<String> newHealthCheckUrls = Sets.asSet("http://foo", "http://bar");
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withDelta(InstanceInfoField.HEALTHCHECK_URLS, newHealthCheckUrls)
                .build();
        doDeltaTest(delta);
    }

    @Test(timeout = 60000)
    public void testDeltaSerializationWithAvro_String() throws Exception {
        String newHomepage = "http://something.random.net";
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withDelta(InstanceInfoField.HOMEPAGE_URL, newHomepage)
                .build();
        doDeltaTest(delta);
    }

    @Test(timeout = 60000)
    public void testDeltaSerializationWithAvro_InstanceStatus() throws Exception {
        InstanceInfo.Status newStatus = InstanceInfo.Status.OUT_OF_SERVICE;
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withDelta(InstanceInfoField.STATUS, newStatus)
                .build();
        doDeltaTest(delta);
    }

    private void doDeltaTest(Delta<?> delta) {
        UpdateInstanceInfo update = new UpdateInstanceInfo(delta);

        assertTrue("Message should be written successfuly to the channel", channel.writeOutbound(update));

        channel.writeInbound(channel.readOutbound());
        Object received = channel.readInbound();
        assertTrue("Expected instance of UpdateInstanceInfo", received instanceof UpdateInstanceInfo);
        assertEquals("Encoded/decoded shall produce identical object", update, received);
    }
}
