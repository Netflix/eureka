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

import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p/>
 * <p>
 * This implementation allows users to plugin their own
 * serialization/deserialization mechanism by reading the annotation provided by
 * specifying the {@link Serializer} and dispatching it to that implementation.
 * </p>
 *
 * @author Karthik Ranganathan
 */
@Provider
@Produces("*/*")
@Consumes("*/*")
public class DiscoveryJerseyProvider implements MessageBodyWriter,
        MessageBodyReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryJerseyProvider.class);

    // Cache the serializers so that they don't have to be instantiated every time
    private static ConcurrentHashMap<Class, ISerializer> serializers = new ConcurrentHashMap<Class, ISerializer>();

    private final EncoderWrapper encoder;
    private final DecoderWrapper decoder;

    public DiscoveryJerseyProvider() {
        this(null, null);
    }

    public DiscoveryJerseyProvider(EncoderWrapper encoder, DecoderWrapper decoder) {
        this.encoder = encoder == null ? CodecWrappers.getEncoder(LegacyJacksonJson.class) : encoder;
        this.decoder = decoder == null ? CodecWrappers.getDecoder(LegacyJacksonJson.class) : decoder;

        if (encoder instanceof CodecWrappers.JacksonJsonMini) {
            throw new UnsupportedOperationException("Encoder: " + encoder.codecName() + "is not supported for the client");
        }

        LOGGER.info("Using encoding codec {}", this.encoder.codecName());
        LOGGER.info("Using decoding codec {}", this.decoder.codecName());
    }

    public EncoderWrapper getEncoder() {
        return encoder;
    }

    public DecoderWrapper getDecoder() {
        return decoder;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.ws.rs.ext.MessageBodyReader#isReadable(java.lang.Class,
     * java.lang.reflect.Type, java.lang.annotation.Annotation[],
     * javax.ws.rs.core.MediaType)
     */
    @Override
    public boolean isReadable(Class serializableClass, Type type,
                              Annotation[] annotations, MediaType mediaType) {
        return checkForAnnotation(serializableClass);
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.ws.rs.ext.MessageBodyReader#readFrom(java.lang.Class,
     * java.lang.reflect.Type, java.lang.annotation.Annotation[],
     * javax.ws.rs.core.MediaType, javax.ws.rs.core.MultivaluedMap,
     * java.io.InputStream)
     */
    @Override
    public Object readFrom(Class serializableClass, Type type,
                           Annotation[] annotations, MediaType mediaType,
                           MultivaluedMap headers, InputStream inputStream)
            throws IOException, WebApplicationException {

        if (decoder.support(mediaType)) {
            try {
                return decoder.decode(inputStream, serializableClass);
            } catch (Error e) {
                LOGGER.error("Unexpected error occurred during de-serialization of discovery data, doing connection "
                        + "cleanup.", e);
                inputStream.close();
                throw e;
            }
        }

        // default to XML encoded with XStream
        ISerializer serializer = getSerializer(serializableClass);
        if (null != serializer) {
            try {
                return serializer.read(inputStream, serializableClass, mediaType);
            } catch (Error e) { // See issue: https://github.com/Netflix/eureka/issues/72 on why we catch Error here.
                LOGGER.error("Unexpected error occurred during de-serialization of discovery data, doing connection "
                        + "cleanup.", e);
                if (null != inputStream) {
                    inputStream.close();
                    LOGGER.error("Unexpected error occurred during de-serialization of discovery data, done connection "
                            + "cleanup.", e);
                }
                throw e;
            }
        } else {
            LOGGER.error("No serializer available for serializable class: " + serializableClass
                    + ", de-serialization will fail.");
            throw new IOException("No serializer available for serializable class: " + serializableClass);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.ws.rs.ext.MessageBodyWriter#getSize(java.lang.Object,
     * java.lang.Class, java.lang.reflect.Type,
     * java.lang.annotation.Annotation[], javax.ws.rs.core.MediaType)
     */
    @Override
    public long getSize(Object serializableObject, Class serializableClass,
                        Type type, Annotation[] annotations, MediaType mediaType) {
        // TODO Auto-generated method stub
        return -1;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.ws.rs.ext.MessageBodyWriter#isWriteable(java.lang.Class,
     * java.lang.reflect.Type, java.lang.annotation.Annotation[],
     * javax.ws.rs.core.MediaType)
     */
    @Override
    public boolean isWriteable(Class serializableClass, Type type,
                               Annotation[] annotations, MediaType mediaType) {
        return checkForAnnotation(serializableClass);
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.ws.rs.ext.MessageBodyWriter#writeTo(java.lang.Object,
     * java.lang.Class, java.lang.reflect.Type,
     * java.lang.annotation.Annotation[], javax.ws.rs.core.MediaType,
     * javax.ws.rs.core.MultivaluedMap, java.io.OutputStream)
     */
    @Override
    public void writeTo(Object serializableObject, Class serializableClass,
                        Type type, Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap headers, OutputStream outputStream)
            throws IOException, WebApplicationException {

        if (encoder.support(mediaType)) {
            encoder.encode(serializableObject, outputStream);
        } else {  // default
            ISerializer serializer = getSerializer(serializableClass);
            if (null != serializer) {
                serializer.write(serializableObject, outputStream, mediaType);
            } else {
                LOGGER.error("No serializer available for serializable class: " + serializableClass
                        + ", serialization will fail.");
                throw new IOException("No serializer available for serializable class: " + serializableClass);
            }
        }
    }

    /**
     * Checks for the {@link java.io.Serializable} annotation for the given class.
     *
     * @param serializableClass The class to be serialized/deserialized.
     * @return true if the annotation is present, false otherwise.
     */
    private boolean checkForAnnotation(Class serializableClass) {
        try {
            Annotation annotation = serializableClass
                    .getAnnotation(Serializer.class);
            if (annotation != null) {
                return true;
            }
        } catch (Throwable th) {
            LOGGER.warn("Exception in checking for annotations", th);
        }
        return false;
    }

    /**
     * Gets the {@link Serializer} implementation for serializing/ deserializing
     * objects.
     * <p/>
     * <p/>
     * The implementation is cached after the first time instantiation and then
     * returned.
     * <p/>
     *
     * @param serializableClass - The class that is to be serialized/deserialized.
     * @return The {@link Serializer} implementation for serializing/
     * deserializing objects.
     */
    @Nullable
    private static ISerializer getSerializer(@SuppressWarnings("rawtypes") Class serializableClass) {
        ISerializer converter = null;
        Annotation annotation = serializableClass.getAnnotation(Serializer.class);
        if (annotation != null) {
            Serializer payloadConverter = (Serializer) annotation;
            String serializer = payloadConverter.value();
            if (serializer != null) {
                converter = serializers.get(serializableClass);
                if (converter == null) {
                    try {
                        converter = (ISerializer) Class.forName(serializer).newInstance();
                    } catch (InstantiationException e) {
                        LOGGER.error("Error creating a serializer.", e);
                    } catch (IllegalAccessException e) {
                        LOGGER.error("Error creating a serializer.", e);
                    } catch (ClassNotFoundException e) {
                        LOGGER.error("Error creating a serializer.", e);
                    }
                    if (null != converter) {
                        serializers.put(serializableClass, converter);
                    }
                }
            }

        }
        return converter;
    }
}
