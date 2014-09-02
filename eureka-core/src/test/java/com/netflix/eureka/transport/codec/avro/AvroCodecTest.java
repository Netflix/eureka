package com.netflix.eureka.transport.codec.avro;

import com.netflix.eureka.transport.base.SampleObject;
import com.netflix.eureka.transport.base.SampleObject.Internal;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class AvroCodecTest {

    @Test
    public void testEncodeDecode() throws Exception {
        AvroCodec avroCodec = new AvroCodec(SampleObject.SAMPLE_OBJECT_MODEL_SET, SampleObject.rootSchema());

        EmbeddedChannel ch = new EmbeddedChannel(avroCodec);

        SampleObject message = new SampleObject(new Internal("stringValue"));
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();
        assertTrue("Expected instance of SampleUserObject", received instanceof SampleObject);
        assertEquals("Encoded/decoded shall produce identical object", message, received);
    }
}