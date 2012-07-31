/*
 * JsonXStream.java
 *  
 * $Header: //depot/commonlibraries/eureka-client/main/src/com/netflix/discovery/converters/JsonXStream.java#2 $ 
 * $DateTime: 2012/07/16 17:00:42 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.converters;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;

/**
 * Per XStream FAQ - Once the XStream instance has been created and configured, it may be 
 * shared across multiple threads allowing objects to be serialized/deserialized 
 * concurrently. The creation and initialization of XStream is quite expensive, so this is
 * the singleton instance that we are reuse.
 * 
 * @author gkim
 */
public class JsonXStream extends XStream {
    
    private final static JsonXStream s_instance = new JsonXStream();
    
    public JsonXStream() {
        super(new JettisonMappedXmlDriver());
        
        registerConverter(new Converters.ApplicationConverter());
        registerConverter(new Converters.ApplicationsConverter());
        registerConverter(new Converters.DataCenterInfoConverter());
        registerConverter(new Converters.InstanceInfoConverter());
        registerConverter(new Converters.LeaseInfoConverter());
        registerConverter(new Converters.MetadataConverter());
        setMode(XStream.NO_REFERENCES);
        processAnnotations(new Class[]{
                InstanceInfo.class,
                Application.class,
                Applications.class
                });
    }
    
    public static JsonXStream getInstance() {
        return s_instance;
    }
}