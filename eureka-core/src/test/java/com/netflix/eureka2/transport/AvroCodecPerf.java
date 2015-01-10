package com.netflix.eureka2.transport;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.codec.avro.AvroCodec;
import com.netflix.eureka2.transport.codec.avro.SchemaReflectData;
import io.netty.channel.embedded.EmbeddedChannel;

import static com.netflix.eureka2.transport.EurekaTransports.REGISTRATION_AVRO_SCHEMA;
import static com.netflix.eureka2.transport.EurekaTransports.REGISTRATION_PROTOCOL_MODEL_SET;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class AvroCodecPerf {

    private final int messageCount;
    private final int loops;

    public AvroCodecPerf(int messageCount, int loops) {
        this.messageCount = messageCount;
        this.loops = loops;
    }

    public void runRegistrations() {
        SchemaReflectData reflectData = new SchemaReflectData(REGISTRATION_AVRO_SCHEMA);
        InstanceInfo instanceInfo = SampleInstanceInfo.EurekaReadServer.build();
        for (int l = 0; l < loops; l++) {
//            AvroCodec codec = new AvroCodec(REGISTRATION_PROTOCOL_MODEL_SET, REGISTRATION_AVRO_SCHEMA);
            AvroCodec codec = new AvroCodec(REGISTRATION_PROTOCOL_MODEL_SET, REGISTRATION_AVRO_SCHEMA, reflectData);
            EmbeddedChannel ch = new EmbeddedChannel(codec);

            for (int i = 0; i < messageCount; i++) {
                assertTrue("Message should be written successfully to the channel", ch.writeOutbound(instanceInfo));
                ch.writeInbound(ch.readOutbound());
                InstanceInfo received = (InstanceInfo) ch.readInbound();
                assertThat(received, is(equalTo(instanceInfo)));
            }
        }
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        int loops = 1000000;
        new AvroCodecPerf(100, loops).runRegistrations();
        double duration = System.currentTimeMillis() - startTime;
        System.out.println("Total execution time=" + duration / 1000 + "sec");
        System.out.println("Loops per second=" + loops / (duration / 1000) + "ms");
        System.out.println("Execution per loop=" + duration / loops + "ms");
    }
}
