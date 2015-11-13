package com.netflix.eureka2.transport.codec;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class EurekaCodecWrapperTest {

    @Test
    public void testFixMe() throws Exception {
    }
//
//    @Test(timeout = 60000)
//    public void testAvroEncodeDecode() throws Exception {
//        encodeDecodeTest(new EurekaAvroCodec(SAMPLE_OBJECT_MODEL_SET, rootSchema()));
//    }
//
//    @Test(timeout = 60000)
//    public void testJsonEncodeDecodec() throws Exception {
//        encodeDecodeTest(new EurekaJsonCodec(SAMPLE_OBJECT_MODEL_SET));
//    }
//
//    private void encodeDecodeTest(EurekaCodec eurekaCodec) {
//        Map<Byte, AbstractNettyCodec> map = new HashMap<>();
//        map.put(CodecType.Json.getVersion(), new EurekaCodecWrapper(eurekaCodec));
//        EmbeddedChannel ch = new EmbeddedChannel(new DynamicNettyCodec(
//                SAMPLE_OBJECT_MODEL_SET,
//                Collections.unmodifiableMap(map),
//                CodecType.Json.getVersion()));
//        assertTrue("Message should be written successfully to the channel", ch.writeOutbound(CONTENT));
//
//        ch.writeInbound(ch.readOutbound());
//        Object received = ch.readInbound();
//
//        assertEquals(CONTENT, received);
//    }
}