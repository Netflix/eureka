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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.converters.jackson.builder.StringInterningAmazonInfoBuilder;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@JsonDeserialize(builder = StringInterningAmazonInfoBuilder.class)
public class AmazonInfo implements DataCenterInfo, UniqueIdentifier {

    private Map<String, String> metadata = new HashMap<String, String>();

    private static DynamicBooleanProperty shouldLogAWSMetadataError;
    private static DynamicIntProperty awsMetaDataReadTimeout;
    private static DynamicIntProperty awsMetaDataConnectTimeout;
    private static DynamicIntProperty awsMetaDataRetries;

    /**
     * When creating an AmazonInfo via {@link com.netflix.appinfo.AmazonInfo.Builder#autoBuild(String)},
     * a fail fast mechanism exist based on the below configuration.
     * If enabled (default to true), the {@link com.netflix.appinfo.AmazonInfo.Builder#autoBuild(String)}
     * method will exit early after failing to load the value for the first metadata key (instanceId),
     * after the expected number of retries as defined by {@link #awsMetaDataRetries}.
     */
    private static DynamicBooleanProperty awsMetaDataFailFastOnFirstLoad;

    private static final String AWS_API_VERSION = "latest";
    private static final String AWS_METADATA_URL = "http://169.254.169.254/" + AWS_API_VERSION + "/meta-data/";

    public enum MetaDataKey {
        instanceId("instance-id"),  // always have this first as we use it as a fail fast mechanism
        amiId("ami-id"),
        instanceType("instance-type"),
        localIpv4("local-ipv4"),
        localHostname("local-hostname"),
        availabilityZone("availability-zone", "placement/"),
        publicHostname("public-hostname"),
        publicIpv4("public-ipv4"),
        mac("mac"),  // mac is declared above vpcId so will be found before vpcId (where it is needed)
        vpcId("vpc-id", "network/interfaces/macs/") {
            @Override
            public URL getURL(String prepend, String mac) throws MalformedURLException {
                return new URL(AWS_METADATA_URL + this.path + mac + "/" + this.name);
            }
        },
        accountId("accountId") {
            private Pattern pattern = Pattern.compile("\"accountId\"\\s?:\\s?\\\"([A-Za-z0-9]*)\\\"");

            @Override
            public URL getURL(String prepend, String append) throws MalformedURLException {
                return new URL("http://169.254.169.254/" + AWS_API_VERSION + "/dynamic/instance-identity/document");
            }

            // no need to use a json deserializer, do a custom regex parse
            @Override
            protected String read(InputStream inputStream) throws IOException {
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                try {
                    String toReturn = null;
                    String inputLine;
                    while ((inputLine = br.readLine()) != null) {
                        Matcher matcher = pattern.matcher(inputLine);
                        if (toReturn == null && matcher.find()) {
                            toReturn = matcher.group(1);
                            // don't break here as we want to read the full buffer for a clean connection close
                        }
                    }

                    return toReturn;
                } finally {
                    br.close();
                }
            }
        };

        protected String name;
        protected String path;

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

        // override to apply prepend and append
        public URL getURL(String prepend, String append) throws MalformedURLException {
            return new URL(AWS_METADATA_URL + path + name);
        }

        protected String read(InputStream inputStream) throws IOException {
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String toReturn;
            try {
                String line = br.readLine();
                toReturn = line;

                while (line != null) {  // need to read all the buffer for a clean connection close
                    line = br.readLine();
                }

                return toReturn;
            } finally {
                br.close();
            }
        }

        public String toString() {
            return getName();
        }
    }


    public static final class Builder {
        private static final Logger logger = LoggerFactory.getLogger(Builder.class);
        private static final int SLEEP_TIME_MS = 100;

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
         *
         * @param namespace the namespace to look for configuration properties.
         * @return the instance information specific to AWS.
         */
        public AmazonInfo autoBuild(String namespace) {
            initProperties(namespace);
            for (MetaDataKey key : MetaDataKey.values()) {
                int numOfRetries = awsMetaDataRetries.get();
                while (numOfRetries-- > 0) {
                    try {
                        String mac = null;
                        if (key == MetaDataKey.vpcId) {
                            mac = result.metadata.get(MetaDataKey.mac.getName());  // mac should be read before vpcId due to declaration order
                        }
                        URL url = key.getURL(null, mac);
                        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
                        uc.setConnectTimeout(awsMetaDataConnectTimeout.get());
                        uc.setReadTimeout(awsMetaDataReadTimeout.get());

                        if (uc.getResponseCode() != HttpURLConnection.HTTP_OK) {  // need to read the error for clean connection close
                            BufferedReader br = new BufferedReader(new InputStreamReader(uc.getErrorStream()));
                            try {
                                while (br.readLine() != null) {
                                    // do nothing but keep reading the line
                                }
                            } finally {
                                br.close();
                            }
                        } else {
                            String value = key.read(uc.getInputStream());
                            if (value != null) {
                                result.metadata.put(key.getName(), value);
                            }
                        }

                        break;
                    } catch (Throwable e) {
                        if (shouldLogAWSMetadataError.get()) {
                            logger.warn("Cannot get the value for the metadata key :" + key + " Reason :", e);
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

                if (key == MetaDataKey.instanceId
                        && awsMetaDataFailFastOnFirstLoad.get()
                        && !result.metadata.containsKey(MetaDataKey.instanceId.getName())) {

                    logger.warn("Skipping the rest of AmazonInfo init as we were not able to load instanceId after " +
                                    "the configured number of retries: {}, per fail fast configuration: {}",
                            awsMetaDataRetries.get(), awsMetaDataFailFastOnFirstLoad.get());
                    break;  // break out of loop and return whatever we have thus far
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
                                namespace + "mt.read_timeout", 5000);
            }
            if (awsMetaDataConnectTimeout == null) {
                awsMetaDataConnectTimeout = com.netflix.config.DynamicPropertyFactory
                        .getInstance().getIntProperty(
                                namespace + "mt.connect_timeout", 2000);
            }
            if (awsMetaDataRetries == null) {
                awsMetaDataRetries = com.netflix.config.DynamicPropertyFactory
                        .getInstance().getIntProperty(namespace + "mt.num_retries",
                                3);
            }
            if (awsMetaDataFailFastOnFirstLoad == null) {
                awsMetaDataFailFastOnFirstLoad = com.netflix.config.DynamicPropertyFactory
                        .getInstance().getBooleanProperty(namespace + "mt.fail_fast_on_first_load",
                                true);
            }
        }
    }

    public AmazonInfo() {
    }

    /**
     * Constructor provided for deserialization framework. It is expected that {@link AmazonInfo} will be built
     * programmatically using {@link AmazonInfo.Builder}.
     *
     * @param name this value is ignored, as it is always set to "Amazon"
     */
    @JsonCreator
    public AmazonInfo(
            @JsonProperty("name") String name,
            @JsonProperty("metadata") HashMap<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public Name getName() {
        return Name.Amazon;
    }

    /**
     * Get the metadata information specific to AWS.
     *
     * @return the map of AWS metadata as specified by {@link MetaDataKey}.
     */
    @JsonProperty("metadata")
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

    @Override
    @JsonIgnore
    public String getId() {
        return get(MetaDataKey.instanceId);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AmazonInfo)) return false;

        AmazonInfo that = (AmazonInfo) o;

        if (metadata != null ? !metadata.equals(that.metadata) : that.metadata != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return metadata != null ? metadata.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "AmazonInfo{" +
                "metadata=" + metadata +
                '}';
    }
}
