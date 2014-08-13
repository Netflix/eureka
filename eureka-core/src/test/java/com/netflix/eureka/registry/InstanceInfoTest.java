package com.netflix.eureka.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

import com.netflix.eureka.SampleInstanceInfo;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;

/**
 * @author David Liu
 */
public class InstanceInfoTest {

    @Test
    public void testInstanceInfoSerializationWithAvro() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();

        Schema schema = ReflectData.get().getSchema(InstanceInfo.class);

        DatumWriter<InstanceInfo> writer = new ReflectDatumWriter<InstanceInfo>(schema);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(bos, null);

        writer.write(instanceInfo, encoder);
        encoder.flush();
        bos.close();

        DatumReader<InstanceInfo> datumReader = new ReflectDatumReader<InstanceInfo>(schema);
        Decoder decoder = DecoderFactory.get().binaryDecoder(bos.toByteArray(), null);
        InstanceInfo newInstanceInfo = datumReader.read(null, decoder);

        assertEquals(instanceInfo, newInstanceInfo);
    }

    @Test
    public void testApplyDelta() {
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();

        assertThat(instanceInfo.getStatus(), equalTo(InstanceInfo.Status.UP));
        assertThat(instanceInfo.getPorts(), containsInAnyOrder(80, 8080));

        DeltaInstanceInfo delta = new DeltaInstanceInfo();
        delta.addDelta("status", InstanceInfo.Status.OUT_OF_SERVICE);
        delta.addDelta("ports", new HashSet<Integer>(Arrays.asList(111, 222)));

        InstanceInfo afterDelta = instanceInfo.applyDelta(delta);
        assertThat(afterDelta.getStatus(), equalTo(InstanceInfo.Status.OUT_OF_SERVICE));
        assertThat(afterDelta.getPorts(), containsInAnyOrder(111, 222));
    }
}
