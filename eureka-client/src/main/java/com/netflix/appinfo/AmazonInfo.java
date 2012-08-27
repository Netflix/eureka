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

package com.netflix.appinfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * An AWS specific {@link DataCenterInfo} implementation.
 * 
 * <p>
 * Gets AWS specific information for registration with eureka by making a HTTP
 * call to an AWS service as recommended by AWS.
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
public class AmazonInfo implements DataCenterInfo {

    private Map<String, String> metadata = new HashMap<String, String>();
    private static DynamicBooleanProperty shouldLogAWSMetadataError;
    private static DynamicIntProperty awsMetaDataReadTimeout;
    private static DynamicIntProperty awsMetaDataConnectTimeout;
    private static DynamicIntProperty awsMetaDataRetries;
    
    public enum MetaDataKey {
        amiId("ami-id"), instanceId("instance-id"), instanceType(
        "instance-type"), localIpv4("local-ipv4"), availabilityZone(
                "availability-zone", "placement/"), publicHostname(
                "public-hostname"), publicIpv4("public-ipv4");

        private String name;
        private String path;

        MetaDataKey(String name) {
            this(name, "");
        }

        MetaDataKey(String name, String path) {
            this.name = name;
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }
    }

    public static final class Builder {
        private static final Logger logger = LoggerFactory
        .getLogger(Builder.class);
        private static final int SLEEP_TIME_MS = 100;
        private final static String AWS_API_VERSION = "latest";
        private final static String AWS_METADATA_URL = "http://169.254.169.254/"
            + AWS_API_VERSION + "/meta-data/";

        @XStreamOmitField
        private AmazonInfo result;

        private Builder() {
            result = new AmazonInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder addMetadata(MetaDataKey key, String value) {
            result.metadata.put(key.getName(), value);
            return this;
        }

        /**
         * Build the {@link InstanceInfo} information.
         * 
         * @return AWS specific instance information.
         */
        public AmazonInfo build() {
            return result;
        }

        /**
         * Build the {@link AmazonInfo} automatically via HTTP calls to instance
         * metadata API.
         * @param namespace the namespace to look for configuration properties.
         * @return the instance information specific to AWS.
         */
        public AmazonInfo autoBuild(String namespace) {
            initProperties(namespace);
            for (MetaDataKey key : MetaDataKey.values()) {
                int numOfRetries = awsMetaDataRetries.get();
                while (numOfRetries-- > 0) {
                    try {
                        URL url = new URL(AWS_METADATA_URL + key.getPath()
                                + key.getName());
                        HttpURLConnection uc = (HttpURLConnection) url
                        .openConnection();
                        uc.setConnectTimeout(awsMetaDataConnectTimeout.get());
                        uc.setReadTimeout(awsMetaDataReadTimeout.get());

                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(uc.getInputStream()));
                        String value = br.readLine();
                        if (value != null) {
                            result.metadata.put(key.getName(), value);
                        }
                    } catch (Throwable e) {
                        if (shouldLogAWSMetadataError.get()) {
                            logger.warn(
                                    "Cannot get the value for the metadata key :"
                                    + key + " Reason :", e);
                        }
                        if (numOfRetries >= 0) {
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

        private void initProperties(String namespace) {
            if (shouldLogAWSMetadataError == null) {
                shouldLogAWSMetadataError = com.netflix.config.DynamicPropertyFactory
            .getInstance().getBooleanProperty(
                    namespace + "logAmazonMetadataErrors", false);
            }
            if (awsMetaDataReadTimeout == null) {
                awsMetaDataReadTimeout = com.netflix.config.DynamicPropertyFactory
            .getInstance().getIntProperty(
                    namespace + "mt.read_timeout", 8000);
            }
            if (awsMetaDataConnectTimeout == null) {
                awsMetaDataConnectTimeout = com.netflix.config.DynamicPropertyFactory
            .getInstance().getIntProperty(
                    namespace + "mt.connect_timeout", 3000);
            }
            if (awsMetaDataRetries == null) {
                awsMetaDataRetries = com.netflix.config.DynamicPropertyFactory
            .getInstance().getIntProperty(namespace + "mt.num_retries",
                    3);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.DataCenterInfo#getName()
     */
    @Override
    public Name getName() {
        return Name.Amazon;
    }

    /**
     * Get the metadata information specific to AWS.
     * 
     * @return the map of AWS metadata as specified by {@link MetaDataKey}.
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Set AWS metadata.
     * 
     * @param metadataMap
     *            the map containing AWS metadata.
     */
    public void setMetadata(Map<String, String> metadataMap) {
        this.metadata = metadataMap;
    }

    /**
     * Gets the AWS metadata specified in {@link MetaDataKey}.
     * 
     * @param key
     *            the metadata key.
     * @return String returning the value.
     */
    public String get(MetaDataKey key) {
        return metadata.get(key.getName());
    }
}
