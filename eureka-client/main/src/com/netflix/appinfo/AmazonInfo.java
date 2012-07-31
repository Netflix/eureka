/*
 * AmazonInfo.java
 *
 * $Header: //depot/commonlibraries/platform/main/ipc/src/com/netflix/appinfo/AmazonInfo.java#1 $
 * $DateTime: 2011/12/02 15:13:11 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Amazon specific metadata associated w/ {@link InstanceInfo}
 *
 * @author gkim
 */
public class AmazonInfo implements DataCenterInfo {

    private static final DynamicBooleanProperty PROP_SHOULD_LOG_AMAZON_METADATA_ERROR = com.netflix.config.DynamicPropertyFactory.getInstance().getBooleanProperty("netflix.appinfo.logAmazonMetadataErrors", false);

    /**
     * Instance metadata available as documented on
     * http://docs.amazonwebservices.com/AWSEC2/latest/DeveloperGuide/index.html?AESDG-chapter-instancedata.html
     */
    public enum MetaDataKey {
        amiId("ami-id"),
        instanceId("instance-id"),
        instanceType("instance-type"),
        localIpv4("local-ipv4"),
        availabilityZone("availability-zone", "placement/"),
        publicHostname("public-hostname"),
        publicIpv4("public-ipv4");

        // real name
        String _name;
        String _path;

        MetaDataKey(String name) {
            this(name, "");
        }

        MetaDataKey(String name, String path){
            _name = name;
            _path = path;
        }

        public String getName() {
            return _name;
        }

        public String getPath() {
            return _path;
        }
    }

    public static final class Builder {
        private static final Logger logger = LoggerFactory.getLogger(Builder.class); 
        private static final int SLEEP_TIME_MS = 100;
        private final static String MT_API_VERSION  = "latest";
        private final static String MT_URL          = "http://169.254.169.254/" + MT_API_VERSION + "/meta-data/";

        private static final DynamicIntProperty MT_READ_TIMEOUT    = com.netflix.config.DynamicPropertyFactory.getInstance().getIntProperty("netflix.appinfo.mt.read_timeout", 8000);
        private static final DynamicIntProperty MT_CONNECT_TIMEOUT = com.netflix.config.DynamicPropertyFactory.getInstance().getIntProperty("netflix.appinfo.mt.connect_timeout", 3000);
        private static final DynamicIntProperty MT_RETRIES = com.netflix.config.DynamicPropertyFactory.getInstance().getIntProperty("netflix.appinfo.mt.num_retries", 3);
        @XStreamOmitField
        private AmazonInfo result;

        private Builder() {
            result = new AmazonInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder addMetadata(MetaDataKey key, String value){
            result.metadata.put(key.getName(), value);
            return this;
        }

        /**
         * Build the {@link InstanceInfo}
         */
        public AmazonInfo build() {
            return result;
        }

        /**
         * Build the {@link AmazonInfo} automatically via HTTP calls to instance
         * metadata API
         */
        public AmazonInfo autoBuild() {
            for(MetaDataKey key : MetaDataKey.values()){
                int numOfRetries = MT_RETRIES.get();
                while(numOfRetries-- > 0) {
                    try {
                        URL url = new URL(MT_URL + key.getPath() + key.getName());
                        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
                        uc.setConnectTimeout(MT_CONNECT_TIMEOUT.get());
                        uc.setReadTimeout(MT_READ_TIMEOUT.get());

                        BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
                        String value = br.readLine();
                        if(value != null){
                            result.metadata.put(key.getName(), value);
                        }
                    }catch(Throwable e){
                        if (PROP_SHOULD_LOG_AMAZON_METADATA_ERROR.get()) {
                            logger.warn("Cannot get the value for the metadata key :" + key + " Reason :", e);
                        }
                        if(numOfRetries >= 0){
                            try {
                                Thread.sleep(SLEEP_TIME_MS);
                            } catch (InterruptedException e1) {
                                
                            }
                            continue;
                        }
                    }
                }
            }
            return result;
        }
    }

    private Map<String,String> metadata = new HashMap<String, String>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Name getName() {
        return Name.Amazon;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> mt){
        this.metadata = mt;
    }

    /**
     * Get metadata value
     */
    public String get(MetaDataKey key) {
        return metadata.get(key.getName());
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("[Amazon Info:\n");

        for(Iterator<Entry<String, String>> iter = metadata.entrySet().iterator();
            iter.hasNext(); ) {
            Entry<String, String> entry = iter.next();
            buf.append('\t').append(entry.getKey()).append('=').
                append(entry.getValue()).append('\n');
        }
        buf.append("]\n");
        return buf.toString();
    }

    public static void main(String[] args) {
        AmazonInfo info = AmazonInfo.Builder.newBuilder().autoBuild();

        System.out.println(info.toString());
    }
}
