package com.netflix.eureka2.eureka1.rest.codec;

import java.io.IOException;

/**
 * @author Tomasz Bak
 */
public interface Eureka1DataCodec {

    enum EncodingFormat {Json, Xml}

    byte[] encode(Object entity, EncodingFormat format, boolean gzip) throws IOException;

    <T> T decode(byte[] bytes, Class<T> bodyType, EncodingFormat format) throws IOException;
}
