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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom provider implementation for Jersey that dispatches to the
 * implementation that serializes/deserializes objects sent to and from eureka
 * server.
 * 
 * <p>
 * This implementation allows users to plugin their own
 * serialization/deserialization mechanism by reading the annotation provided by
 * specifying the {@link Serializer} and dispatching it to that implementation.
 * </p>
 * 
 * @author Karthik Ranganathan
 * 
 */
@Provider
@Produces("*/*")
@Consumes("*/*")
public class DiscoveryJerseyProvider implements MessageBodyWriter,
MessageBodyReader {
    private static final Logger LOGGER = LoggerFactory
    .getLogger(DiscoveryJerseyProvider.class);

    // Cache the serializers so that they don't have to be instantiated every
    // time/
    private static ConcurrentHashMap<Class, ISerializer> serializers = new ConcurrentHashMap<Class, ISerializer>();

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
        try {
            return getSerializer(serializableClass).read(inputStream,
                    serializableClass, mediaType);
        } catch (Throwable th) {
           throw new RuntimeException("Cannot read the object for :" + serializableClass, th);
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
        try {
            ISerializer serializer = getSerializer(serializableClass);
            serializer.write(serializableObject, outputStream, mediaType);
        } catch (Throwable th) {
            throw new IOException("Cannot write the object for :" + serializableClass, th);
        }
    }

    /**
     * Checks for the {@link Serializable} annotation for the given class.
     * 
     * @param serializableClass
     *            The class to be serialized/deserialized.
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
     * 
     * <p>
     * The implementation is cached after the first time instantiation and then
     * returned.
     * <p>
     * 
     * @param serializableClass
     *            - The class that is to be serialized/deserialized.
     * @return The {@link Serializer} implementation for serializing/
     *         deserializing objects.
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     */
    private ISerializer getSerializer(Class serializableClass)
    throws InstantiationException, IllegalAccessException,
    ClassNotFoundException {
        ISerializer converter = null;
        Annotation annotation = serializableClass
        .getAnnotation(Serializer.class);
        if (annotation != null) {
            Serializer payloadConverter = (Serializer) annotation;
            String serializer = payloadConverter.value();
            if (serializer != null) {
                converter = serializers.get(serializableClass);
                if (converter == null) {
                    converter = (ISerializer) Class.forName(serializer)
                    .newInstance();
                    serializers.put(serializableClass, converter);
                }
            }

        }
        return converter;
    }
}