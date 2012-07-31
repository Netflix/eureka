/*
 * Converters.java
 *
 * $Header: //depot/commonlibraries/eureka-client/main/src/com/netflix/discovery/converters/Converters.java#3 $
 * $DateTime: 2012/07/29 11:24:24 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.converters;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * Converters for common appinfo elements
 *
 * @author gkim
 */
public final class Converters {
    private static final String UNMARSHAL_ERROR = "UNMARSHAL_ERROR";
    private static final Counter UNMARSHALL_ERROR_COUNTER = Monitors.newCounter(UNMARSHAL_ERROR);
    public static final String NODE_LEASE = "leaseInfo";
    public static final String NODE_METADATA = "metadata";
    public static final String NODE_DATACENTER = "dataCenterInfo";
    public static final String NODE_INSTANCE = "instance";
    public static final String NODE_APP = "application";
    public static final String NODE_APPS = "applications";


    private static final Logger logger = LoggerFactory.getLogger(Converters.class);


    public static class ApplicationsConverter implements Converter {

        private static final String VERSIONS_DELTA = "versions_delta";
        private static final String APPS_HASHCODE = "apps_hashcode";


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class clazz) {
            return Applications.class == clazz;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            Applications apps = (Applications)source;
            writer.startNode(VERSIONS_DELTA);
            writer.setValue(apps.getVersion().toString());
            writer.endNode();
            writer.startNode(APPS_HASHCODE);
            writer.setValue(apps.getAppsHashCode());
            writer.endNode();
            for(Application app : apps.getRegisteredApplications()){
                writer.startNode(NODE_APP);
                context.convertAnother(app);
                writer.endNode();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Applications apps = new Applications();
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                
                String nodeName = reader.getNodeName();

                if(NODE_APP.equals(nodeName)){
                    apps.addApplication((Application)context.convertAnother(apps,
                            Application.class));
                }
                else if(VERSIONS_DELTA.equals(nodeName)) {
                    apps.setVersion(Long.valueOf(reader.getValue()));
                } 
                else if(APPS_HASHCODE.equals(nodeName)) {
                    apps.setAppsHashCode(reader.getValue());
                } 
                reader.moveUp();
            }
            return apps;
        }
    }

    public static class ApplicationConverter implements Converter {

        private static final String ELEM_NAME = "name";

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class clazz) {
            return Application.class == clazz;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            Application app = (Application)source;

            writer.startNode(ELEM_NAME);
            writer.setValue(app.getName());
            writer.endNode();

            for(InstanceInfo instanceInfo : app.getInstances()){
                writer.startNode(NODE_INSTANCE);
                context.convertAnother(instanceInfo);
                writer.endNode();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Application app = new Application();

            while (reader.hasMoreChildren()) {
                reader.moveDown();

                String nodeName = reader.getNodeName();

                if(ELEM_NAME.equals(nodeName)){
                    app.setName(reader.getValue());
                }else if(NODE_INSTANCE.equals(nodeName)){
                    app.addInstance((InstanceInfo)context.convertAnother(app,
                            InstanceInfo.class));
                }
                reader.moveUp();
            }
            return app;
        }
    }

    public static class InstanceInfoConverter implements Converter {

        private static final String ELEM_OVERRIDDEN_STATUS = "overriddenstatus";
        private static final String ELEM_HOST = "hostName";
        private static final String ELEM_APP = "app";
        private static final String ELEM_IP = "ipAddr";
        private static final String ELEM_VERSION = "version";
        private static final String ELEM_SRC_VERSION = "sourceVersion";
        private static final String ELEM_SID = "sid";
        private static final String ELEM_STATUS = "status";
        private static final String ELEM_PORT = "port";
        private static final String ELEM_SECURE_PORT = "securePort";
        private static final String ELEM_COUNTRY_ID = "countryId";
        private static final String ELEM_IDENTIFYING_ATTR = "identifyingAttribute";
        private static final String ATTR_ENABLED = "enabled";

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class clazz) {
            return InstanceInfo.class == clazz;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            InstanceInfo info = (InstanceInfo)source;

            writer.startNode(ELEM_HOST);
            writer.setValue(info.getHostName());
            writer.endNode();

            writer.startNode(ELEM_APP);
            writer.setValue(info.getAppName());
            writer.endNode();

            writer.startNode(ELEM_IP);
            writer.setValue(info.getIPAddr());
            writer.endNode();

            if(! "unknown".equals(info.getVersion())){
                writer.startNode(ELEM_VERSION);
                writer.setValue(info.getVersion());
                writer.endNode();
            }
            /*
            if(! "unknown".equals(info.getSourceVersion())){
                writer.startNode(ELEM_SRC_VERSION);
                writer.setValue(info.getSourceVersion());
                writer.endNode();
            }

            if(! ("unknown".equals(info.getSID()) || "na".equals(info.getSID()))){
                writer.startNode(ELEM_SID);
                writer.setValue(info.getSID());
                writer.endNode();
            }
            */

            writer.startNode(ELEM_STATUS);
            writer.setValue(getStatus(info));
            writer.endNode();
            
            writer.startNode(ELEM_OVERRIDDEN_STATUS);
            writer.setValue(info.getOverriddenStatus().name());
            writer.endNode();

            writer.startNode(ELEM_PORT);
            writer.addAttribute(ATTR_ENABLED, String.valueOf(info.isPortEnabled(PortType.UNSECURE)));
            writer.setValue(String.valueOf(info.getPort()));
            writer.endNode();

            writer.startNode(ELEM_SECURE_PORT);
            writer.addAttribute(ATTR_ENABLED, String.valueOf(info.isPortEnabled(PortType.SECURE)));
            writer.setValue(String.valueOf(info.getSecurePort()));
            writer.endNode();

            writer.startNode(ELEM_COUNTRY_ID);
            writer.setValue(String.valueOf(info.getCountryId()));
            writer.endNode();
            
            /*
            writer.startNode(ELEM_IDENTIFYING_ATTR);
            writer.setValue(info.getIdentifyingAttribute());
            writer.endNode();
            */

            if(info.getDataCenterInfo() != null){
                writer.startNode(NODE_DATACENTER);
                //This is needed for backward compat. for now.
                if(info.getDataCenterInfo().getName() == Name.Amazon){
                    writer.addAttribute("class", "com.netflix.appinfo.AmazonInfo");
                }else {
                    writer.addAttribute("class", "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo");
                }
                context.convertAnother(info.getDataCenterInfo());
                writer.endNode();
            }

            if(info.getLeaseInfo() != null) {
                writer.startNode(NODE_LEASE);
                context.convertAnother(info.getLeaseInfo());
                writer.endNode();
            }

            if(info.getMetadata() != null){
                writer.startNode(NODE_METADATA);
                //for backward compat. for now
                if(info.getMetadata().size() == 0){
                    writer.addAttribute("class", "java.util.Collections$EmptyMap");
                }
                context.convertAnother(info.getMetadata());
                writer.endNode();
            }
            autoMarshalEligible(source, writer);
        }

        /**
         * This is overridden by the discovery server
         */
        public String getStatus(InstanceInfo info){
            return info.getStatus().name();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();

            while(reader.hasMoreChildren()) {
                reader.moveDown();

                String nodeName = reader.getNodeName();

                if(ELEM_HOST.equals(nodeName)){
                    builder.setHostName(reader.getValue());
                }else if(ELEM_APP.equals(nodeName)) {
                    builder.setAppName(reader.getValue());
                }else if(ELEM_IP.equals(nodeName)){
                    builder.setIPAddr(reader.getValue());
                }else if(ELEM_VERSION.equals(nodeName)){
                    builder.setVersion(reader.getValue());
                }else if(ELEM_SRC_VERSION.equals(nodeName)){
                    builder.setSourceVersion(reader.getValue());
                }else if(ELEM_SID.equals(nodeName)){
                    builder.setSID(reader.getValue());
                }else if(ELEM_IDENTIFYING_ATTR.equals(nodeName)){
                    //nothing;
                }else if(ELEM_STATUS.equals(nodeName)){
                    builder.setStatus(InstanceStatus.toEnum(reader.getValue()));
                } else if(ELEM_OVERRIDDEN_STATUS.equals(nodeName)){
                        builder.setOverriddenStatus(InstanceStatus.toEnum(reader.getValue()));
                }else if(ELEM_PORT.equals(nodeName)){
                    builder.setPort(Integer.valueOf(reader.getValue()).intValue());
                    //Defaults to true
                    builder.enablePort(PortType.UNSECURE,
                            ! "false".equals(reader.getAttribute(ATTR_ENABLED)) );
                }else if(ELEM_SECURE_PORT.equals(nodeName)){
                    builder.setSecurePort(Integer.valueOf(reader.getValue()).intValue());
                    //Defaults to false
                    builder.enablePort(PortType.SECURE,
                            "true".equals(reader.getAttribute(ATTR_ENABLED)) );
                }else if(ELEM_COUNTRY_ID.equals(nodeName)){
                    builder.setCountryId(Integer.valueOf(reader.getValue()).intValue());
                }else if(NODE_DATACENTER.equals(nodeName)){
                    builder.setDataCenterInfo((DataCenterInfo)context.convertAnother(builder,
                            DataCenterInfo.class));
                }else if(NODE_LEASE.equals(nodeName)){
                    builder.setLeaseInfo((LeaseInfo)context.convertAnother(builder,
                            LeaseInfo.class));
                }else if(NODE_METADATA.equals(nodeName)){
                    builder.setMetadata((Map<String,String>)context.convertAnother(builder,
                            Map.class));
                }else {
                    autoUnmarshalEligible(reader, builder.getRawInstance());
                }

                reader.moveUp();
            }

            return builder.build();
        }
    }

    public static class DataCenterInfoConverter implements Converter {

        private static final String ELEM_NAME = "name";

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class clazz) {
            return DataCenterInfo.class.isAssignableFrom(clazz);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            DataCenterInfo info = (DataCenterInfo)source;

            writer.startNode(ELEM_NAME);
            //For backward compat. for now
            writer.setValue(info.getName().name());
            writer.endNode();

            if(info.getName() == Name.Amazon) {
                AmazonInfo aInfo = (AmazonInfo)info;
                writer.startNode(NODE_METADATA);
                //for backward compat. for now
                if(aInfo.getMetadata().size() == 0){
                    writer.addAttribute("class", "java.util.Collections$EmptyMap");
                }
                context.convertAnother(aInfo.getMetadata());
                writer.endNode();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            DataCenterInfo info = null;
            while(reader.hasMoreChildren()) {
                reader.moveDown();

                if(ELEM_NAME.equals(reader.getNodeName())){
                    if(DataCenterInfo.Name.Amazon.name().equalsIgnoreCase(reader.getValue())){
                        info = new AmazonInfo();
                    }else {
                        info = InstanceInfo.DefaultDataCenterInfo.INSTANCE;
                    }
                }else if(NODE_METADATA.equals(reader.getNodeName())){
                    if(info.getName() == Name.Amazon){
                        ((AmazonInfo)info).setMetadata((Map<String,String>)context.convertAnother(info, Map.class));
                    }
                }

                reader.moveUp();
            }
            return info;
        }
    }

    public static class LeaseInfoConverter implements Converter {

        private static final String ELEM_RENEW_INT = "renewalIntervalInSecs";
        private static final String ELEM_DURATION = "durationInSecs";
        private static final String ELEM_REG_TIMESTAMP = "registrationTimestamp";
        private static final String ELEM_LAST_RENEW_TIMETSTAMP = "lastRenewalTimestamp";
        private static final String ELEM_EVICTION_TIMESTAMP = "evictionTimestamp";
        private static final String ELEM_CLOCK = "clock";

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public boolean canConvert(Class clazz) {
            return LeaseInfo.class == clazz;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            LeaseInfo info = (LeaseInfo)source;

            writer.startNode(ELEM_RENEW_INT);
            writer.setValue(String.valueOf(info.getRenewalIntervalInSecs()));
            writer.endNode();

            writer.startNode(ELEM_DURATION);
            writer.setValue(String.valueOf(info.getDurationInSecs()));
            writer.endNode();

            writer.startNode(ELEM_REG_TIMESTAMP);
            writer.setValue(String.valueOf(info.getRegistrationTimestamp()));
            writer.endNode();

            writer.startNode(ELEM_LAST_RENEW_TIMETSTAMP);
            writer.setValue(String.valueOf(info.getRenewalTimestamp()));
            writer.endNode();

            writer.startNode(ELEM_EVICTION_TIMESTAMP);
            writer.setValue(String.valueOf(info.getEvictionTimestamp()));
            writer.endNode();

            writer.startNode(ELEM_CLOCK);
            writer.setValue(String.valueOf(info.getClock()));
            writer.endNode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {

            LeaseInfo.Builder builder = LeaseInfo.Builder.newBuilder();

            while(reader.hasMoreChildren()) {
                reader.moveDown();

                String nodeName = reader.getNodeName();
                String nodeValue = reader.getValue();
                if(nodeValue == null) {
                    continue;
                }

                long longValue = 0;
                try {
                    longValue =  Long.valueOf(nodeValue).longValue();
                }catch(NumberFormatException ne){
                    continue;
                }

                if(ELEM_CLOCK.equals(nodeName)) {
                    builder.setClock(longValue);
                }else if(ELEM_DURATION.equals(nodeName)){
                    builder.setDurationInSecs((int)longValue);
                }else if(ELEM_EVICTION_TIMESTAMP.equals(nodeName)){
                    builder.setEvictionTimestamp(longValue);
                }else if(ELEM_LAST_RENEW_TIMETSTAMP.equals(nodeName)){
                    builder.setRenewalTimestamp(longValue);
                }else if(ELEM_REG_TIMESTAMP.equals(nodeName)){
                    builder.setRegistrationTimestamp(longValue);
                }else if(ELEM_RENEW_INT.equals(nodeName)) {
                    builder.setRenewalIntervalInSecs((int)longValue);
                }
                reader.moveUp();
            }
            return builder.build();
        }
    }

    public static class MetadataConverter implements Converter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
            return Map.class.isAssignableFrom(type);
        }

        /**
         * {@inheritDoc}
         */

        @Override
        @SuppressWarnings("unchecked")
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            Map<String,String> map = (Map<String,String>) source;

            for (Iterator<Entry<String, String>> iter =
                    map.entrySet().iterator(); iter.hasNext();) {
                Entry<String,String> entry = iter.next();

                writer.startNode(entry.getKey());
                writer.setValue(entry.getValue());
                writer.endNode();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return unmarshalMap(reader, context);
        }


        private Map<String, String> unmarshalMap(HierarchicalStreamReader reader,
                UnmarshallingContext context) {

            Map<String,String> map = Collections.emptyMap();

            while (reader.hasMoreChildren()) {
                if(map == Collections.EMPTY_MAP){
                    map = new HashMap<String, String>();
                }
                reader.moveDown();
                String key = reader.getNodeName();
                String value = reader.getValue();
                reader.moveUp();

                map.put(key, value);
            }
            return map;
        }


    }

    /**
     * Marshal all the objects containing an @Auto annotation automatically to XML elements
     * @param o - The object's fields that needs to be marshalled
     * @param writer - The writer for which to write the XML element to
     */
    private static void autoMarshalEligible(Object o,
            HierarchicalStreamWriter writer) {
        try {
            Class c = o.getClass();
            Field[] fields = c.getDeclaredFields();
            Annotation annotation = null;
            for (Field f : fields) {
                annotation = f.getAnnotation(Auto.class);
                if (annotation != null) {
                    f.setAccessible(true);
                    if (f.get(o) != null) {
                        writer.startNode(f.getName());
                        writer.setValue(String.valueOf(f.get(o)));
                        writer.endNode();
                    }

                }
            }
        } catch (Throwable th) {
            logger.error("Error in marshalling the object", th);
        }
    }

    /**
     * Unmarshal all the xml elements to their field values if the fields have the @Auto anottation defined
     * @param reader - The reader where the xml elements can be read
     * @param o - The object for which the value of fields need to be populated
     */
    private static void autoUnmarshalEligible(HierarchicalStreamReader reader,
            Object o) {
        try {
            String nodeName = reader.getNodeName();
            Class c = o.getClass();
            Field f = null;
            try {
                f = c.getDeclaredField(nodeName);
            } catch (NoSuchFieldException e) {
                UNMARSHALL_ERROR_COUNTER.increment();
            }
            if (f == null) {
                return;
            }
            Annotation annotation = f.getAnnotation(Auto.class);
            if (annotation == null) {
                logger.warn("Missing Annotation for Field " + f.getName());
                return;
            }
            f.setAccessible(true);

            String value = reader.getValue();
            Class returnClass = f.getType();
            if (value != null) {
                if (!String.class.equals(returnClass)) {

                    Method method = returnClass.getDeclaredMethod("valueOf",
                            java.lang.String.class);
                    Object valueObject = method.invoke(returnClass, value);
                    f.set(o, valueObject);
                } else {
                    f.set(o, value);

                }
            }
        } catch (Throwable th) {
            logger.error("Error in unmarshalling the object:", th);
        }
    }
}
