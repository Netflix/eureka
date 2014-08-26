package com.netflix.eureka.transport.codec.avro;

import com.netflix.eureka.transport.base.SampleObject;
import com.netflix.eureka.transport.base.SampleObject.InternalA;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class AvroCodecTest {

    @Test
    public void testEncodeDecode() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new AvroCodec(SampleObject.TRANSPORT_MODEL));

        SampleObject message = new SampleObject(new InternalA("stringValue"));
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();
        assertTrue("Expected instance of SampleUserObject", received instanceof SampleObject);
        assertEquals("Encoded/decoded shall produce identical object", message, received);
    }
}