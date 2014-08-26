package com.netflix.eureka.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.netflix.eureka.Sets;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

        assertThat(instanceInfo, equalTo(newInstanceInfo));
    }

    @Test
    public void testApplyDelta() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();

        assertThat(instanceInfo.getStatus(), equalTo(InstanceInfo.Status.UP));
        List<Integer> ports = new ArrayList<Integer>();
        for (int i : instanceInfo.getPorts()) {
            ports.add(i);
        }
        assertThat(ports, containsInAnyOrder(80, 8080));

        Delta delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion()+1)
                .withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.OUT_OF_SERVICE)
                .build();

        InstanceInfo afterDelta = instanceInfo.applyDelta(delta);
        assertThat(afterDelta.getStatus(), equalTo(InstanceInfo.Status.OUT_OF_SERVICE));
    }

    @Test
    public void testProduceNullDeltasIfMismatchedIds() throws Exception {
        InstanceInfo oldInstanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo newInstanceInfo = SampleInstanceInfo.ZuulServer.build();  // different id

        Set<Delta<?>> deltas = oldInstanceInfo.diffNewer(newInstanceInfo);
        assertThat(deltas, nullValue());
    }

    @Test
    public void testProduceSetOfDeltas() throws Exception {
        InstanceInfo oldInstanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        // fake a new InstanceInfo that is different in all fields (except id)
        InstanceInfo newInstanceInfo = SampleInstanceInfo.ZuulServer.builder()
                .withId(oldInstanceInfo.getId())
                .withPorts(Sets.asSet(111,222))
                .withSecurePorts(Sets.asSet(555,666))
                .withStatus(InstanceInfo.Status.DOWN)
                .build();

        Set<Delta<?>> deltas = oldInstanceInfo.diffNewer(newInstanceInfo);
        assertThat(deltas.size(), equalTo(14));

        for (Delta<?> delta : deltas) {
            oldInstanceInfo = oldInstanceInfo.applyDelta(delta);
        }

        assertThat(oldInstanceInfo, equalTo(newInstanceInfo));
    }
}
