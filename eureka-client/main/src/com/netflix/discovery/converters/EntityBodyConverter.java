/*
 * EntityBodyConverter.java
 *  
 * $Header: //depot/commonlibraries/eureka-client/main/src/com/netflix/discovery/converters/EntityBodyConverter.java#2 $ 
 * $DateTime: 2012/07/29 11:24:24 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.converters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.core.MediaType;

import com.netflix.discovery.provider.ISerializer;
import com.thoughtworks.xstream.XStream;

/**
 * Custom entity body serialization/de-serialization for discovery related objects
 *  
 * @author gkim
 */
public class EntityBodyConverter implements ISerializer {

    /**
     * {@inheritDoc}
     */
    public Object read(InputStream is, Class type, MediaType mediaType)
            throws IOException {
        XStream xstream = null;
         
        if(MediaType.APPLICATION_JSON_TYPE.equals(mediaType)){
            xstream = JsonXStream.getInstance();
        }else if(MediaType.APPLICATION_XML_TYPE.equals(mediaType)){
            xstream = XmlXStream.getInstance();
        }
        if(xstream != null) {
            return xstream.fromXML(is);
        }else {
            throw new IllegalArgumentException("Content-type: " + mediaType.getType() + 
                    " is currently not supported for " + type.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void write(Object object, OutputStream os, MediaType mediaType) 
        throws IOException {
        XStream xstream = null;
        if(MediaType.APPLICATION_JSON_TYPE.equals(mediaType)){
            xstream = JsonXStream.getInstance();
        }else if(MediaType.APPLICATION_XML_TYPE.equals(mediaType)){
            xstream = XmlXStream.getInstance();
        }
        if(xstream != null) {
            xstream.toXML(object, os);
        }else {
            throw new IllegalArgumentException("Content-type: " + mediaType.getType() + 
                    " is currently not supported for " + object.getClass().getName());
        }
    }
}
