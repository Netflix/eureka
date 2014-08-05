package com.netflix.eureka.registry;

import static org.junit.Assert.*;

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
}
