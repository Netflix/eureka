package com.netflix.eureka.transport;

import java.util.HashSet;

import com.netflix.eureka.utils.Sets;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfoField;
import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.transport.codec.avro.AvroCodec;
import com.netflix.eureka.transport.utils.AvroUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.avro.Schema;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

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

    @Test
    public void testDeltaSerializationWithAvro_HashSetInt() throws Exception {
        HashSet<Integer> newPorts = Sets.asSet(111, 222);
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.PORTS, newPorts)
                .build();

        doDeltaTest(delta);
    }

    @Test
    public void testDeltaSerializationWithAvro_HashSetString() throws Exception {
        HashSet<String> newHealthCheckUrls = Sets.asSet("http://foo", "http://bar");
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.HEALTHCHECK_URLS, newHealthCheckUrls)
                .build();
        doDeltaTest(delta);
    }

    @Test
    public void testDeltaSerializationWithAvro_String() throws Exception {
        String newHomepage = "http://something.random.net";
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.HOMEPAGE_URL, newHomepage)
                .build();
        doDeltaTest(delta);
    }

    @Test
    public void testDeltaSerializationWithAvro_InstanceStatus() throws Exception {
        InstanceInfo.Status newStatus = InstanceInfo.Status.OUT_OF_SERVICE;
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
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
