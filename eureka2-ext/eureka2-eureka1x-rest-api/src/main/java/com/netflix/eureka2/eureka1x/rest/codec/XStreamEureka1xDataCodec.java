package com.netflix.eureka2.eureka1x.rest.codec;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import com.netflix.discovery.converters.EntityBodyConverter;

/**
 * @author Tomasz Bak
 */
public class XStreamEureka1xDataCodec implements Eureka1xDataCodec {

    private final EntityBodyConverter converter = new EntityBodyConverter();

    @Override
    public byte[] encode(Object entity, EncodingFormat format, boolean gzip) throws IOException {
        MediaType mediaType = format == EncodingFormat.Json ? MediaType.APPLICATION_JSON_TYPE : MediaType.APPLICATION_XML_TYPE;

        ByteArrayOutputStream bufos = new ByteArrayOutputStream();
        if (gzip) {
            GZIPOutputStream gos = new GZIPOutputStream(bufos);
            converter.write(entity, gos, mediaType);
            gos.close();
        } else {
            converter.write(entity, bufos, mediaType);
            bufos.close();
        }
        return bufos.toByteArray();
    }
}
