package com.netflix.eureka2.transport.codec.avro;

import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.base.SampleObject;
import com.netflix.eureka2.transport.codec.AbstractEurekaCodec;
import com.netflix.eureka2.transport.codec.DynamicEurekaCodec;
import com.netflix.eureka2.transport.codec.json.JsonCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.eureka2.transport.base.SampleObject.CONTENT;
import static com.netflix.eureka2.transport.base.SampleObject.SAMPLE_OBJECT_MODEL_SET;
import static com.netflix.eureka2.transport.base.SampleObject.rootSchema;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class AvroCodecTest {

    @Test(timeout = 60000)
    public void testEncodeDecode() throws Exception {
        Map<Byte, AbstractEurekaCodec> map = new HashMap<>();
        map.put(EurekaTransports.Codec.Avro.getVersion(), new AvroCodec(SAMPLE_OBJECT_MODEL_SET, rootSchema()));
        EmbeddedChannel ch = new EmbeddedChannel(new DynamicEurekaCodec(
                SAMPLE_OBJECT_MODEL_SET,
                Collections.unmodifiableMap(map),
                EurekaTransports.Codec.Avro.getVersion()));

        assertTrue("Message should be written successfully to the channel", ch.writeOutbound(CONTENT));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();
        assertTrue("Expected instance of SampleUserObject", received instanceof SampleObject);
        assertEquals("Encoded/decoded shall produce identical object", CONTENT, received);
    }
}