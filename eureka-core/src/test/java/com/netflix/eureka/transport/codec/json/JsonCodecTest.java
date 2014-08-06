package com.netflix.eureka.transport.codec.json;

import com.netflix.eureka.transport.base.SampleObject;
import com.netflix.eureka.transport.base.SampleObject.InternalA;
import com.netflix.eureka.transport.base.SampleObject.InternalB;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class JsonCodecTest {

    @Test
    public void testCodec() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec(SampleObject.TRANSPORT_MODEL));

        SampleObject message = new SampleObject(new InternalA("stringValue"), new InternalB(123));
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();

        assertEquals(message, received);
    }
}