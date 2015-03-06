package com.netflix.eureka2.eureka1x.rest.codec;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
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
        MediaType mediaType = getMediaType(format);

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

    @Override
    public <T> T decode(byte[] is, Class<T> bodyType, EncodingFormat format) throws IOException {
        MediaType mediaType = getMediaType(format);
        return (T) converter.read(new ByteArrayInputStream(is), bodyType, mediaType);
    }

    private static MediaType getMediaType(EncodingFormat format) {
        return format == EncodingFormat.Json ? MediaType.APPLICATION_JSON_TYPE : MediaType.APPLICATION_XML_TYPE;
    }
}
