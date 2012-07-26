package com.netflix.niws;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * This abstract class ease the development of {@link MessageBodyReader}s and
 * {@link MessageBodyWriter}.
 * 
 * @author stonse
 * @param <T>
 *                the type that can be read and written
 * @see MessageBodyReader
 * @see MessageBodyWriter
 * NOTE: Inspired by restlet
 */
public abstract class AbstractProvider<T> implements MessageBodyWriter<T>,
        MessageBodyReader<T> {

    

    /**
     * Returns the size of the given objects.
     * 
     * @param object
     *                the object to check the size
     * @return the size of the object, or -1, if it is not direct readable from
     *         the object.
     * @see MessageBodyWriter#getSize(Object, Class, Type, Annotation[],
     *      MediaType)
     */
    public abstract long getSize(T object, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType);

    public boolean isReadable(Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {
        return type.isAssignableFrom(supportedClass());
    }

    public boolean isWriteable(Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {
        return supportedClass().isAssignableFrom(type);
        // mainClass.isAssignableFrom(subClass);
    }

    /**
     * @param genericType
     *                The generic {@link Type} to convert to.
     * @param annotations
     *                the annotations of the artefact to convert to
     * @see javax.ws.rs.ext.MessageBodyReader#readFrom(java.lang.Class,
     *      javax.ws.rs.core.MediaType, javax.ws.rs.core.MultivaluedMap,
     *      java.io.InputStream)
     */
    public abstract T readFrom(Class<T> type, Type genericType,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, String> httpResponseHeaders,
            InputStream entityStream) throws IOException;

    /**
     * Returns the class object supported by this provider.
     * 
     * @return the class object supported by this provider.
     */
    protected Class<?> supportedClass() {
        throw new UnsupportedOperationException(
                "You must implement method "
                        + this.getClass().getName()
                        + ".supportedClass(), if you do not implement isReadable(...) or isWriteable(...)");
    }

    /**
     * @see MessageBodyWriter#writeTo(Object, Class, Type, Annotation[],
     *      MediaType, MultivaluedMap, OutputStream)
     */
    public abstract void writeTo(T object, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException;
}