/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.discovery.provider;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.CodecWrappers.LegacyJacksonJson;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom provider implementation for Jersey that dispatches to the
 * implementation that serializes/deserializes objects sent to and from eureka
 * server.
 *
 * @author Karthik Ranganathan
 */
@Provider
@Produces({"application/json", "application/xml"})
@Consumes("*/*")
public class DiscoveryJerseyProvider implements MessageBodyWriter<Object>, MessageBodyReader<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryJerseyProvider.class);

    private final EncoderWrapper jsonEncoder;
    private final DecoderWrapper jsonDecoder;

    // XML support is maintained for legacy/custom clients. These codecs are used only on the server side only, while
    // Eureka client is using JSON only.
    private final EncoderWrapper xmlEncoder;
    private final DecoderWrapper xmlDecoder;

    public DiscoveryJerseyProvider() {
        this(null, null);
    }

    public DiscoveryJerseyProvider(EncoderWrapper jsonEncoder, DecoderWrapper jsonDecoder) {
        this.jsonEncoder = jsonEncoder == null ? CodecWrappers.getEncoder(LegacyJacksonJson.class) : jsonEncoder;
        this.jsonDecoder = jsonDecoder == null ? CodecWrappers.getDecoder(LegacyJacksonJson.class) : jsonDecoder;
        LOGGER.info("Using JSON encoding codec {}", this.jsonEncoder.codecName());
        LOGGER.info("Using JSON decoding codec {}", this.jsonDecoder.codecName());

        if (jsonEncoder instanceof CodecWrappers.JacksonJsonMini) {
            throw new UnsupportedOperationException("Encoder: " + jsonEncoder.codecName() + "is not supported for the client");
        }

        this.xmlEncoder = CodecWrappers.getEncoder(CodecWrappers.XStreamXml.class);
        this.xmlDecoder = CodecWrappers.getDecoder(CodecWrappers.XStreamXml.class);

        LOGGER.info("Using XML encoding codec {}", this.xmlEncoder.codecName());
        LOGGER.info("Using XML decoding codec {}", this.xmlDecoder.codecName());
    }

    @Override
    public boolean isReadable(Class serializableClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return isSupportedMediaType(mediaType) && isSupportedCharset(mediaType) && isSupportedEntity(serializableClass);
    }

    @Override
    public Object readFrom(Class serializableClass, Type type,
                           Annotation[] annotations, MediaType mediaType,
                           MultivaluedMap headers, InputStream inputStream) throws IOException {
        DecoderWrapper decoder;
        if (MediaType.MEDIA_TYPE_WILDCARD.equals(mediaType.getSubtype())) {
            decoder = xmlDecoder;
        } else if ("json".equalsIgnoreCase(mediaType.getSubtype())) {
            decoder = jsonDecoder;
        } else {
            decoder = xmlDecoder; // default
        }

        try {
            return decoder.decode(inputStream, serializableClass);
        } catch (Throwable e) {
            if (e instanceof Error) { // See issue: https://github.com/Netflix/eureka/issues/72 on why we catch Error here.
                closeInputOnError(inputStream);
                throw new WebApplicationException(e, createErrorReply(500, e, mediaType));
            }
            LOGGER.debug("Cannot parse request body", e);
            throw new WebApplicationException(e, createErrorReply(400, "cannot parse request body", mediaType));
        }
    }

    @Override
    public long getSize(Object serializableObject, Class serializableClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public boolean isWriteable(Class serializableClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return isSupportedMediaType(mediaType) && isSupportedEntity(serializableClass);
    }

    @Override
    public void writeTo(Object serializableObject, Class serializableClass,
                        Type type, Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap headers, OutputStream outputStream) throws IOException, WebApplicationException {
        EncoderWrapper encoder = "json".equalsIgnoreCase(mediaType.getSubtype()) ? jsonEncoder : xmlEncoder;

        // XML codec may not be available
        if (encoder == null) {
            throw new WebApplicationException(createErrorReply(400, "No codec available to serialize content type " + mediaType, mediaType));
        }

        encoder.encode(serializableObject, outputStream);
    }

    private boolean isSupportedMediaType(MediaType mediaType) {
        if (MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType)) {
            return true;
        }
        if (MediaType.APPLICATION_XML_TYPE.isCompatible(mediaType)) {
            return xmlDecoder != null;
        }
        return false;
    }

    /**
     * As content is cached, we expect both ends use UTF-8 always. If no content charset encoding is explicitly
     * defined, UTF-8 is assumed as a default.
     * As legacy clients may use ISO 8859-1 we accept it as well, although result may be unspecified if
     * characters out of ASCII 0-127 range are used.
     */
    private static boolean isSupportedCharset(MediaType mediaType) {
        Map<String, String> parameters = mediaType.getParameters();
        if (parameters == null || parameters.isEmpty()) {
            return true;
        }
        String charset = parameters.get("charset");
        return charset == null
                || "UTF-8".equalsIgnoreCase(charset)
                || "ISO-8859-1".equalsIgnoreCase(charset);
    }

    /**
     * Checks for the {@link Serializer} annotation for the given class.
     *
     * @param entityType The class to be serialized/deserialized.
     * @return true if the annotation is present, false otherwise.
     */
    private static boolean isSupportedEntity(Class<?> entityType) {
        try {
            Annotation annotation = entityType.getAnnotation(Serializer.class);
            if (annotation != null) {
                return true;
            }
        } catch (Throwable th) {
            LOGGER.warn("Exception in checking for annotations", th);
        }
        return false;
    }

    private static Response createErrorReply(int status, Throwable cause, MediaType mediaType) {
        StringBuilder sb = new StringBuilder(cause.getClass().getName());
        if (cause.getMessage() != null) {
            sb.append(": ").append(cause.getMessage());
        }
        return createErrorReply(status, sb.toString(), mediaType);
    }

    private static Response createErrorReply(int status, String errorMessage, MediaType mediaType) {
        String message;
        if (MediaType.APPLICATION_JSON_TYPE.equals(mediaType)) {
            message = "{\"error\": \"" + errorMessage + "\"}";
        } else {
            message = "<error><message>" + errorMessage + "</message></error>";
        }
        return Response.status(status).entity(message).type(mediaType).build();
    }

    private static void closeInputOnError(InputStream inputStream) {
        if (inputStream != null) {
            LOGGER.error("Unexpected error occurred during de-serialization of discovery data, done connection cleanup");
            try {
                inputStream.close();
            } catch (IOException e) {
                LOGGER.debug("Cannot close input", e);
            }
        }
    }
}
