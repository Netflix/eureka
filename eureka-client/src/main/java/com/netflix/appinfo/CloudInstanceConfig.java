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

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;

/**
 * An {@link InstanceInfo} configuration for AWS cloud deployments.
 *
 * <p>
 * The information required for registration with eureka by a combination of
 * user-supplied values as well as querying AWS instance metadata.An utility
 * class {@link AmazonInfo} helps in retrieving AWS specific values. Some of
 * that information including <em>availability zone</em> is used for determining
 * which eureka server to communicate to.
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
public class CloudInstanceConfig extends PropertiesInstanceConfig {
    private static final Logger logger = LoggerFactory
    .getLogger(CloudInstanceConfig.class);
    private static final DynamicPropertyFactory INSTANCE = com.netflix.config.DynamicPropertyFactory
    .getInstance();
    private DynamicBooleanProperty propValidateInstanceId;
    private DataCenterInfo info;

    public CloudInstanceConfig() {
        initCloudInstanceConfig(namespace);
    }
    public CloudInstanceConfig(String namespace) {
        super(namespace);
        initCloudInstanceConfig(namespace);
    }
    private void initCloudInstanceConfig(String namespace) {
        propValidateInstanceId = INSTANCE.getBooleanProperty(namespace
                + "validateInstanceId", true);
        info = initDataCenterInfo();
    }

    private DataCenterInfo initDataCenterInfo() {
        DataCenterInfo info;
        try {
            info = AmazonInfo.Builder.newBuilder().autoBuild(namespace);
            logger.info("Datacenter is: " + Name.Amazon);
        } catch (Throwable e) {
            logger.error("Cannot initialize amazon info :", e);
            throw new RuntimeException(e);
        }
        // Instance id being null means we could not get the amazon metadata
        AmazonInfo amazonInfo = (AmazonInfo) info;
        if (amazonInfo.get(MetaDataKey.instanceId) == null) {
            if (propValidateInstanceId.get()) {
                throw new RuntimeException(
                        "Your datacenter is defined as cloud but we are not able to get the amazon metadata to register. \n"
                        + "Set the property " + namespace + "validateInstanceId to false to ignore the metadata call");
            }
            // The property to not validate instance ids may be set for
            // development and in that scenario, populate instance id
            // and public hostname with the hostname of the machine
            else {
                Map<String, String> metadataMap = new HashMap<String, String>();
                metadataMap.put(MetaDataKey.instanceId.getName(),
                        super.getIpAddress());
                metadataMap.put(MetaDataKey.publicHostname.getName(),
                        super.getHostName(false));
                amazonInfo.setMetadata(metadataMap);
            }
        }
        // This might be a case of VPC where the instance id is not null, but
        // public hostname might be null
        else if ((amazonInfo.get(MetaDataKey.publicHostname) == null)
                && (amazonInfo.get(MetaDataKey.localIpv4) != null)) {
            amazonInfo.getMetadata().put(MetaDataKey.publicHostname.getName(),
                    (amazonInfo.get(MetaDataKey.localIpv4)));
        }
        return info;
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.appinfo.AbstractInstanceConfig#getHostName()
     */
    @Override
    public String getHostName(boolean refresh) {
        if (refresh) {
            refreshAmazonInfo();
        }
        return ((AmazonInfo) info).get(MetaDataKey.publicHostname);
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.appinfo.AbstractInstanceConfig#getDataCenterInfo()
     */
    @Override
    public DataCenterInfo getDataCenterInfo() {
        return info;
    }

    /**
     * Refresh instance info - currently only used when in AWS cloud
     * as a public ip can change whenever an EIP is associated or dissociated.
     */
    public synchronized void refreshAmazonInfo() {
        try {
                AmazonInfo newInfo = AmazonInfo.Builder.newBuilder()
                .autoBuild(namespace);
                String newHostname = newInfo.get(MetaDataKey.publicHostname);
                String existingHostname = ((AmazonInfo) info)
                .get(MetaDataKey.publicHostname);
                if (newHostname != null
                        && !newHostname.equals(existingHostname)) {
                    // public dns has changed on us, re-sync it
                    logger.warn("The public hostname changed from : "
                            + existingHostname + " => " + newHostname);
                    this.info = newInfo;
                }
       } catch (Throwable t) {
            logger.error("Cannot refresh the Amazon Info ", t);
        }
    }

}
