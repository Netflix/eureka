package com.netflix.eureka2.transport.codec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.codec.EurekaCodec;
import com.netflix.eureka2.codec.avro.EurekaAvroCodec;
import com.netflix.eureka2.codec.json.EurekaJsonCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static com.netflix.eureka2.codec.SampleObject.CONTENT;
import static com.netflix.eureka2.codec.SampleObject.SAMPLE_OBJECT_MODEL_SET;
import static com.netflix.eureka2.codec.SampleObject.rootSchema;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class EurekaCodecWrapperTest {

    @Test(timeout = 60000)
    public void testAvroEncodeDecode() throws Exception {
        encodeDecodeTest(new EurekaAvroCodec(SAMPLE_OBJECT_MODEL_SET, rootSchema()));
    }

    @Test(timeout = 60000)
    public void testJsonEncodeDecodec() throws Exception {
        encodeDecodeTest(new EurekaJsonCodec(SAMPLE_OBJECT_MODEL_SET));
    }

    private void encodeDecodeTest(EurekaCodec eurekaCodec) {
        Map<Byte, AbstractNettyCodec> map = new HashMap<>();
        map.put(CodecType.Json.getVersion(), new EurekaCodecWrapper(eurekaCodec));
        EmbeddedChannel ch = new EmbeddedChannel(new DynamicNettyCodec(
                SAMPLE_OBJECT_MODEL_SET,
                Collections.unmodifiableMap(map),
                CodecType.Json.getVersion()));
        assertTrue("Message should be written successfully to the channel", ch.writeOutbound(CONTENT));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();

        assertEquals(CONTENT, received);
    }
}