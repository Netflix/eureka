package com.netflix.eureka2.eureka1x.rest.codec;

import java.io.IOException;

/**
 * @author Tomasz Bak
 */
public interface Eureka1xDataCodec {
    enum EncodingFormat {Json, Xml}

    byte[] encode(Object entity, EncodingFormat format, boolean gzip) throws IOException;
}
