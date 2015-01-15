package com.netflix.eureka2.transport.codec.avro;

import com.netflix.eureka2.transport.base.SampleObject;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static com.netflix.eureka2.transport.base.SampleObject.CONTENT;
import static com.netflix.eureka2.transport.base.SampleObject.SAMPLE_OBJECT_MODEL_SET;
import static com.netflix.eureka2.transport.base.SampleObject.rootSchema;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class AvroCodecTest {

    @Test
    public void testEncodeDecode() throws Exception {
        AvroCodec avroCodec = new AvroCodec(SAMPLE_OBJECT_MODEL_SET, rootSchema());

        EmbeddedChannel ch = new EmbeddedChannel(avroCodec);

        assertTrue("Message should be written successfully to the channel", ch.writeOutbound(CONTENT));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();
        assertTrue("Expected instance of SampleUserObject", received instanceof SampleObject);
        assertEquals("Encoded/decoded shall produce identical object", CONTENT, received);
    }
}