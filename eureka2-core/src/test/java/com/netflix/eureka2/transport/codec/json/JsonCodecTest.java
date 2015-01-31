package com.netflix.eureka2.transport.codec.json;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static com.netflix.eureka2.transport.base.SampleObject.CONTENT;
import static com.netflix.eureka2.transport.base.SampleObject.SAMPLE_OBJECT_MODEL_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class JsonCodecTest {

    @Test(timeout = 60000)
    public void testCodec() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec(SAMPLE_OBJECT_MODEL_SET));
        assertTrue("Message should be written successfully to the channel", ch.writeOutbound(CONTENT));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();

        assertEquals(CONTENT, received);
    }
}