package com.netflix.rx.eureka.transport.codec.json;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static com.netflix.rx.eureka.transport.base.SampleObject.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class JsonCodecTest {

    @Test
    public void testCodec() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec(SAMPLE_OBJECT_MODEL_SET));
        assertTrue("Message should be written successfully to the channel", ch.writeOutbound(CONTENT));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();

        assertEquals(CONTENT, received);
    }
}