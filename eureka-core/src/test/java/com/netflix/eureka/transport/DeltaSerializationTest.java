package com.netflix.eureka.transport;

import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.Sets;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfoField;
import com.netflix.eureka.transport.codec.avro.ConfigurableReflectData;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * @author David Liu
 */
public class DeltaSerializationTest {

    private InstanceInfo instanceInfo;

    DatumWriter<Delta> writer;
    ByteArrayOutputStream bos;
    Encoder encoder;

    DatumReader<Delta> datumReader;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
            Schema schema = new ConfigurableReflectData(EurekaTransports.DISCOVERY_MODEL).getSchema(Delta.class);

            writer = new ReflectDatumWriter<Delta>(schema);
            bos = new ByteArrayOutputStream();
            encoder = EncoderFactory.get().binaryEncoder(bos, null);

            datumReader = new ReflectDatumReader<Delta>(schema);
        }

        @Override
        protected void after() {
            try {
                bos.close();
            } catch (Exception e) {}
        }
    };


    @Test
    public void testDeltaSerializationWithAvro_HashSetInt() throws Exception {
        HashSet<Integer> newPorts = Sets.asSet(111, 222);
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.PORTS, newPorts)
                .build();

        writer.write(delta, encoder);
        encoder.flush();

        Decoder decoder = DecoderFactory.get().binaryDecoder(bos.toByteArray(), null);
        Delta newDelta = datumReader.read(null, decoder);

        InstanceInfo newInstanceInfo = instanceInfo.applyDelta(newDelta);
        assertThat(newInstanceInfo.getPorts(), equalTo(newPorts));
        assertThat(newInstanceInfo.getPorts(), not(equalTo(instanceInfo.getPorts())));
        assertThat(newInstanceInfo.getId(), equalTo(instanceInfo.getId()));
    }

    @Test
    public void testDeltaSerializationWithAvro_HashSetString() throws Exception {
        HashSet<String> newHealthCheckUrls = Sets.asSet("http://foo", "http://bar");
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.HEALTHCHECK_URLS, newHealthCheckUrls)
                .build();

        writer.write(delta, encoder);
        encoder.flush();

        Decoder decoder = DecoderFactory.get().binaryDecoder(bos.toByteArray(), null);
        Delta newDelta = datumReader.read(null, decoder);

        InstanceInfo newInstanceInfo = instanceInfo.applyDelta(newDelta);
        assertThat(newInstanceInfo.getHealthCheckUrls(), equalTo(newHealthCheckUrls));
        assertThat(newInstanceInfo.getHealthCheckUrls(), not(equalTo(instanceInfo.getHealthCheckUrls())));
        assertThat(newInstanceInfo.getId(), equalTo(instanceInfo.getId()));
    }

    @Test
    public void testDeltaSerializationWithAvro_String() throws Exception {
        String newHomepage = "http://something.random.net";
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.HOMEPAGE_URL, newHomepage)
                .build();

        writer.write(delta, encoder);
        encoder.flush();

        Decoder decoder = DecoderFactory.get().binaryDecoder(bos.toByteArray(), null);
        Delta newDelta = datumReader.read(null, decoder);

        InstanceInfo newInstanceInfo = instanceInfo.applyDelta(newDelta);
        assertThat(newInstanceInfo.getHomePageUrl(), equalTo(newHomepage));
        assertThat(newInstanceInfo.getHomePageUrl(), not(equalTo(instanceInfo.getHomePageUrl())));
        assertThat(newInstanceInfo.getId(), equalTo(instanceInfo.getId()));
    }

    @Test
    public void testDeltaSerializationWithAvro_InstanceStatus() throws Exception {
        InstanceInfo.Status newStatus = InstanceInfo.Status.OUT_OF_SERVICE;
        Delta<?> delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withVersion(instanceInfo.getVersion() + 1)
                .withDelta(InstanceInfoField.STATUS, newStatus)
                .build();

        writer.write(delta, encoder);
        encoder.flush();

        Decoder decoder = DecoderFactory.get().binaryDecoder(bos.toByteArray(), null);
        Delta newDelta = datumReader.read(null, decoder);

        InstanceInfo newInstanceInfo = instanceInfo.applyDelta(newDelta);
        assertThat(newInstanceInfo.getStatus(), equalTo(newStatus));
        assertThat(newInstanceInfo.getStatus(), not(equalTo(instanceInfo.getStatus())));
        assertThat(newInstanceInfo.getId(), equalTo(instanceInfo.getId()));
    }

}
