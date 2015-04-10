package com.netflix.eureka2.transport.codec.json;

import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.eureka2.transport.codec.AbstractEurekaCodec;
import com.netflix.eureka2.transport.codec.DynamicEurekaCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
        Map<Byte, AbstractEurekaCodec> map = new HashMap<>();
        map.put(Codec.Json.getVersion(), new JsonCodec(SAMPLE_OBJECT_MODEL_SET));
        EmbeddedChannel ch = new EmbeddedChannel(new DynamicEurekaCodec(
                SAMPLE_OBJECT_MODEL_SET,
                Collections.unmodifiableMap(map),
                Codec.Json.getVersion()));
        assertTrue("Message should be written successfully to the channel", ch.writeOutbound(CONTENT));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();

        assertEquals(CONTENT, received);
    }
}