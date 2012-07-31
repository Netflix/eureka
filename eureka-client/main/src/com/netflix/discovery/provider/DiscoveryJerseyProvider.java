package com.netflix.discovery.provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Provider serializes all Objects of type IResponse. It can
 * deserialize to ...
 *
 * @author stonse
 */

public class DiscoveryJerseyProvider implements MessageBodyWriter,
MessageBodyReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryJerseyProvider.class);

    private static ConcurrentHashMap<Class, ISerializer> serializers = new ConcurrentHashMap<Class, ISerializer>();

    @Override
    public boolean isReadable(Class serializableClass, Type type, Annotation[] annotations,
            MediaType mediaType) {
       return checkForAnnotation(serializableClass);
    }

    @Override
    public Object readFrom(Class serializableClass, Type type, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap headers, InputStream inputStream)
            throws IOException, WebApplicationException {
        try {
        return getSerializer(serializableClass).read(inputStream, serializableClass, mediaType);
        }
        catch (Throwable th) {
            LOGGER.warn("Cannot read the object for :" + serializableClass, th);
        }
        return null;
    }

    @Override
    public long getSize(Object serializableObject, Class serializableClass, Type type, Annotation[] annotations,
            MediaType mediaType) {
        // TODO Auto-generated method stub
        return -1;
    }

    @Override
    public boolean isWriteable(Class serializableClass, Type type, Annotation[] annotations,
            MediaType mediaType) {
        return checkForAnnotation(serializableClass);
    }

    @Override
    public void writeTo(Object serializableObject, Class serializableClass, Type type, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap headers, OutputStream outputStream)
            throws IOException, WebApplicationException {
        try {
            ISerializer serializer = getSerializer(serializableClass);
            serializer.write(serializableObject, outputStream, mediaType);
        }
        catch (Throwable th) {
            LOGGER.warn("Cannot write the object for :" + serializableClass, th);
        }
    }
    
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
    
    private ISerializer getSerializer(Class serializableClass) throws InstantiationException, IllegalAccessException, ClassNotFoundException {  
        ISerializer converter = null;
        Annotation annotation = serializableClass.getAnnotation(Serializer.class);
        if (annotation != null) {
        Serializer payloadConverter =  (Serializer)annotation;
        String serializer = payloadConverter.value();
       if (serializer != null) {
            converter = serializers.get(serializableClass);
            if (converter == null) {
                converter = (ISerializer) Class.forName(serializer).newInstance();
                serializers.put(serializableClass, converter);
            }
        }
       
     }
        return converter;
    }
}