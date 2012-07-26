package com.netflix.niws;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

/**
 * This Provider serializes all Objects of type IResponse. It can
 * deserialize to ...
 *
 * @author stonse
 */
@Provider
@Produces("*/*")
@Consumes("*/*")
public class NIWSProvider extends AbstractProvider<IPayload> {
    private static final String PLATFORM_NIWSPROVIDER_MARSHALL = "PLATFORM:NIWS:NIWSPROVIDER:MARSHALL";
    private static final Timer MARSHALL_TIMER = Monitors.newTimer(PLATFORM_NIWSPROVIDER_MARSHALL);
	private static final String PLATFORM_NIWSPROVIDER_UNMARSHALL = "PLATFORM:NIWS:NIWSPROVIDER:UNMARSHALL";
    private static final Timer UNMARSHALL_TIMER = Monitors.newTimer(PLATFORM_NIWSPROVIDER_UNMARSHALL);
    private static final Logger LOGGER = LoggerFactory.getLogger(NIWSProvider.class);

    private static ConcurrentHashMap<String, NIWSSerializer> serializers = new ConcurrentHashMap<String, NIWSSerializer>();
    
	/**
     * @see javax.ws.rs.ext.MessageBodyWriter#getSize(java.lang.Object)
     */
    @Override
    public long getSize(IPayload object, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    /**
     * @see MessageBodyReader#isReadable(Class, Type, Annotation[])
     */
    @Override
    public boolean isReadable(Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {
        if (IPayload.class.isAssignableFrom(type)){
        	return true;
        }else{
        	return false;
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType) {

        for (Annotation annotation: annotations){
    		if (annotation.annotationType().equals(com.netflix.niws.PayloadConverter.class)){
    			return true;
    		}
    	}
        //check for PayloadConverter annotation on the type
        PayloadConverter clsAnno = type.getAnnotation(PayloadConverter.class);
        if(clsAnno != null){
            return true;
        }else {
            //special case
        	//if the object is of type IPayload then we should
        	 if (IPayload.class.isAssignableFrom(type)){
             	return true;
             }else{
             	return false;
             }
        }
    }

    /**
     * @see MessageBodyReader#readFrom(Class, Type, MediaType, Annotation[],
     *      MultivaluedMap, InputStream)
     */
    @Override
    public IPayload readFrom(Class<IPayload> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, String> httpResponseHeaders,
			InputStream entityStream) throws IOException {

        try {
        	for (Annotation annotation: annotations){
        		if (annotation.annotationType().equals(com.netflix.niws.PayloadConverter.class)){
        		    // which PayloadConverter are we using?
        			PayloadConverter payloadConverter = (PayloadConverter)annotation;
        			return unmarshalEntityBody(payloadConverter.value(), type, mediaType, entityStream);
        		}
        	}
        	// if we have reached here ..
        	// this is not coming from any JSR-311 server (could be from a client using this provider)
        	// detect the payload converter used
        	if (httpResponseHeaders.containsKey(Constants.HEADER_PAYLOAD_CONVERTER)){
        		String payloadConverterName = httpResponseHeaders.getFirst(Constants.HEADER_PAYLOAD_CONVERTER);
        		LOGGER.debug("--- request to read entiy from a client (non-jsr) ---");
        		LOGGER.debug("payloadConverterName:" + payloadConverterName);
                return unmarshalEntityBody(payloadConverterName, type, mediaType, entityStream);
        	}

        	//check for PayloadConverter annotation on the type
        	PayloadConverter clsAnno = type.getAnnotation(PayloadConverter.class);
        	if(clsAnno != null){
                return unmarshalEntityBody(clsAnno.value(), type, mediaType, entityStream);
        	}

        	// maybe we try with the default (XStream XML)?
        	if (httpResponseHeaders.containsKey(Constants.CONTENT_TYPE)){
        		String contentType = httpResponseHeaders.getFirst(Constants.CONTENT_TYPE);
        		if (contentType.contains(Constants.CONTENT_TYPE_APPLICATION_JSON)
        				||  contentType.contains(Constants.CONTENT_TYPE_APPLICATION_XML)){
        			String	payloadConverterName = "com.netflix.niws.XStreamPayloadConverter";
        			LOGGER.debug("Cant find Annotation of PayloadConverter nor A Header, using payloadConverterName:" + payloadConverterName);
        			return unmarshalEntityBody(payloadConverterName, type, mediaType, entityStream);
        		}else if (contentType.contains(Constants.CONTENT_TYPE_APPLICATION_OCTET_STREAM)){
        			if (type instanceof Serializable){
        				LOGGER.debug("Object is Serializable - will use JavaSerializerPayloadConverter");
        				String	payloadConverterName = "com.netflix.niws.JavaSerializerPayloadConverter";
        				LOGGER.debug("Cant find Annotation of PayloadConverter nor A Header, using payloadConverterName:" + payloadConverterName);
        				return unmarshalEntityBody(payloadConverterName, type, mediaType, entityStream);
        			}
        		}
        	}


        	// if we have reached here - we have no clue who is calling and what method to use to unmarshall this object
            throw new IllegalArgumentException("No associated PayloadConverters found for: " +
                    type.getName());
        } catch (IOException e) {
            // Don't wrap any existing IOExceptions in case they happen to implement extra
            // interfaces or are particular subclasses that are meaningful to the caller.
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }


    /**
     * @see MessageBodyWriter#writeTo(Object, Class, Type, Annotation[],
     *      MediaType, MultivaluedMap, OutputStream)
     */
    @Override
    public void writeTo(IPayload object, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders,
			OutputStream entityStream) throws IOException {
        try {
            String path = null;
            boolean payloadConverterAnnotated = false;
            PayloadConverter payloadConverter = null;
        	for (Annotation annotation: annotations){
        	    Class<? extends Annotation> annotationType = annotation.annotationType();
        		if (annotationType.equals(com.netflix.niws.PayloadConverter.class)){
        		    // which PayloadConverter are we using?
        			payloadConverter = (PayloadConverter)annotation;
        			payloadConverterAnnotated = true;
        			//IMP: add header before streaming
        			httpHeaders.add(Constants.HEADER_PAYLOAD_CONVERTER, payloadConverter.value());
        			if (httpHeaders.containsKey("Content-Type")){
        				LOGGER.debug("Content_Type:" + httpHeaders.getFirst("Content-Type"));
        			}
        		} else if (annotationType.equals(javax.ws.rs.Path.class)) {
                    path = ((javax.ws.rs.Path) annotation).value();
                } 
        	}
        	if (payloadConverterAnnotated) {
                marshalEntityBody(payloadConverter.value(), object, mediaType, entityStream, path);
                return;
        	}
            //check for PayloadConverter annotation on the type
            PayloadConverter clsAnno = type.getAnnotation(PayloadConverter.class);
            if(clsAnno != null){
                marshalEntityBody(clsAnno.value(), object, mediaType, entityStream, path);
                return;
            }
        	// if we have reached here ..
            // this write is most possibly coming from our NIWS client
            // in which case we will look at the HttpHeaders
            if (httpHeaders.containsKey(Constants.HEADER_PAYLOAD_CONVERTER)){
        		String payloadConverterName = (String) httpHeaders.getFirst(Constants.HEADER_PAYLOAD_CONVERTER);
        		marshalEntityBody(payloadConverterName, object, mediaType, entityStream, path);
        		return;
        	}

            if (httpHeaders.containsKey(Constants.CONTENT_TYPE)){
            	String contentType = null;
            	if (httpHeaders.getFirst(Constants.CONTENT_TYPE) instanceof MediaType){
            	    MediaType mt = null;
                    mt = (MediaType) httpHeaders.getFirst(Constants.CONTENT_TYPE);
                    contentType = mt.toString();
            	} else if (httpHeaders.getFirst(Constants.CONTENT_TYPE) instanceof String){
            	    contentType = (String) httpHeaders.getFirst(Constants.CONTENT_TYPE);
                } else {
                    throw new IllegalArgumentException("Unable to determine PayloadConverter for: " +
                            object.getClass().getName());
                }
        		if (contentType.equalsIgnoreCase(Constants.CONTENT_TYPE_APPLICATION_JSON)
        				||  contentType.equalsIgnoreCase(Constants.CONTENT_TYPE_APPLICATION_XML)){
        			String	payloadConverterName = "com.netflix.niws.XStreamPayloadConverter";
        			LOGGER.debug("Cant find Annotation of PayloadConverter nor A Header, using payloadConverterName:" + payloadConverterName);
        			marshalEntityBody(payloadConverterName, object, mediaType, entityStream, path);
        			return;
        		}else if (contentType.equalsIgnoreCase(Constants.CONTENT_TYPE_APPLICATION_OCTET_STREAM)){
        			if (object instanceof Serializable){
        				LOGGER.debug("Object is Serizliable - will use JavaSerializerPayloadConverter");
        				String	payloadConverterName = "com.netflix.niws.JavaSerializerPayloadConverter";
        				LOGGER.debug("Cant find Annotation of PayloadConverter nor A Header, using payloadConverterName:" + payloadConverterName);
	        			marshalEntityBody(payloadConverterName, object, mediaType, entityStream, path);
	        			return;
        			}
        		}
        	}
        	throw new IllegalArgumentException("No associated PayloadConverters found for: " +
        	        object.getClass().getName());
        } catch (IOException e) {
            // Don't wrap any existing IOExceptions in case they happen to implement extra
            // interfaces or are particular subclasses that are meaningful to the caller.
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    
    private void marshalEntityBody(String converterClsName,
            IPayload object,
            MediaType mediaType,
            OutputStream entityStream, String path) throws Exception {
    	Stopwatch s = MARSHALL_TIMER.start();
        String contentType = mediaType.getType()+"/"+mediaType.getSubtype();
        NIWSSerializer poc = getSerializer(converterClsName);
        poc.write(object, entityStream, contentType);
        s.stop();
        }

    private IPayload unmarshalEntityBody(String converterClsName,
            Class<IPayload> type,
            MediaType mediaType,
            InputStream entityStream) throws Exception {
    	Stopwatch s = UNMARSHALL_TIMER.start();
    	NIWSSerializer poc = getSerializer(converterClsName);
        String contentType = mediaType.getType()+"/"+mediaType.getSubtype();
        IPayload payload =  poc.read(entityStream, type, contentType);
        s.stop();
        return payload;
    }
    
   
    
    private NIWSSerializer getSerializer(String className) throws Exception {
        NIWSSerializer serializer = serializers.get(className);
        if (serializer == null) {
            serializer = new NIWSSerializer((IPayloadObjectConverter) Class.forName(className).newInstance());
            serializers.putIfAbsent(className, serializer);
        }
        return serializer;
    }
}