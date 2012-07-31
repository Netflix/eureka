/*
 * EntityBodyConverter.java
 *  
 * $Header: //depot/commonlibraries/eureka-client/main/src/com/netflix/discovery/converters/EntityBodyConverter.java#1 $ 
 * $DateTime: 2012/07/16 11:58:15 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.converters;

import com.netflix.niws.IPayload;
import com.netflix.niws.IPayloadObjectConverter;
import com.thoughtworks.xstream.XStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.ws.rs.core.MediaType;

/**
 * Custom entity body serialization/de-serialization for discovery related objects
 *  
 * @author gkim
 */
public class EntityBodyConverter implements IPayloadObjectConverter {

    /**
     * {@inheritDoc}
     */
    public IPayload read(InputStream is, Class<IPayload> type, String contentType)
            throws IOException {
        XStream xstream = null;
        String singleContentType = (contentType != null) ? 
                contentType.split(",")[0] : null;
        
        if(MediaType.APPLICATION_JSON.equals(singleContentType)){
            xstream = JsonXStream.getInstance();
        }else if(MediaType.APPLICATION_XML.equals(singleContentType)){
            xstream = XmlXStream.getInstance();
        }
        if(xstream != null) {
            return (IPayload)xstream.fromXML(is);
        }else {
            throw new IllegalArgumentException("Content-type: " + singleContentType + 
                    " is currently not supported for " + type.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void write(Object object, OutputStream os, String contentType) 
        throws IOException {
        XStream xstream = null;
        String singleContentType = (contentType != null) ? 
                contentType.split(",")[0] : null;
        if(MediaType.APPLICATION_JSON.equals(singleContentType)){
            xstream = JsonXStream.getInstance();
        }else if(MediaType.APPLICATION_XML.equals(singleContentType)){
            xstream = XmlXStream.getInstance();
        }
        if(xstream != null) {
            xstream.toXML(object, os);
        }else {
            throw new IllegalArgumentException("Content-type: " + singleContentType + 
                    " is currently not supported for " + object.getClass().getName());
        }
    }
}
