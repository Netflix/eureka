/*
 * Copyright 2012 Netflix, Inc.
 *f
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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;

/**
 * The class that initializes information required for registration with
 * <tt>Eureka Server</tt> and to be discovered by other components.
 * 
 * <p>
 * The information required for registration is provided by the user by passing
 * the configuration defined by the contract in {@link EurekaInstanceConfig}
 * }.AWS clients can either use or extend {@link CloudInstanceConfig
 * }.Other non-AWS clients can use or extend either
 * {@link MyDataCenterInstanceConfig} or very basic
 * {@link AbstractInstanceConfig}.
 * </p>
 * 
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
public class ApplicationInfoManager {
    private static final Logger logger = LoggerFactory
    .getLogger(ApplicationInfoManager.class);
    private static final ApplicationInfoManager instance = new ApplicationInfoManager();
    private InstanceInfo instanceInfo;
    private EurekaInstanceConfig config;

    private ApplicationInfoManager() {
    }

    public static ApplicationInfoManager getInstance() {
        return instance;
    }

    public void initComponent(EurekaInstanceConfig config) {
        try {
            this.config = config;
            // Build the lease information to be passed to the server based
            // on config
            LeaseInfo.Builder leaseInfoBuilder = LeaseInfo.Builder
            .newBuilder()
            .setRenewalIntervalInSecs(
                    config.getLeaseRenewalIntervalInSeconds())
                    .setDurationInSecs(
                            config.getLeaseExpirationDurationInSeconds());

            // Builder the instance information to be registered with eureka
            // server
            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();

            builder.setNamespace(config.getNamespace())
            .setAppName(config.getAppname())
            .setDataCenterInfo(config.getDataCenterInfo())
            .setIPAddr(config.getIpAddress())
            .setHostName(config.getHostName(false))
            .setPort(config.getNonSecurePort())
            .enablePort(PortType.UNSECURE,
                    config.isNonSecurePortEnabled())
            .setSecurePort(config.getSecurePort())
            .enablePort(PortType.SECURE, config.getSecurePortEnabled())
            .setVIPAddress(config.getVirtualHostName())
            .setSecureVIPAddress(config.getSecureVirtualHostName())
            .setHomePageUrl(config.getHomePageUrlPath(),
                            config.getHomePageUrl())
            .setStatusPageUrl(config.getStatusPageUrlPath(),
                              config.getStatusPageUrl())
            .setHealthCheckUrls(config.getHealthCheckUrlPath(),
                                config.getHealthCheckUrl(),
                                config.getSecureHealthCheckUrl())
            .setASGName(config.getASGName());

            // Start off with the STARTING state to avoid traffic
            if (!config.isInstanceEnabledOnit()) {
                builder.setStatus(InstanceStatus.STARTING);
            }

            // Add any user-specific metadata information
            for (Map.Entry<String, String> mapEntry : config.getMetadataMap()
                    .entrySet()) {
                String key = mapEntry.getKey();
                String value = mapEntry.getValue();
                builder.add(key, value);
            }
            instanceInfo = builder.build();
            instanceInfo.setLeaseInfo(leaseInfoBuilder.build());

        } catch (Throwable e) {
            throw new RuntimeException(
                    "Failed to initialize ApplicationInfoManager", e);
        }
    }

    /**
     * Gets the information about this instance that is registered with eureka.
     * 
     * @return information about this instance that is registered with eureka.
     */
    public InstanceInfo getInfo() {
        return instanceInfo;
    }

    /**
     * Register user-specific instance meta data. Application can send any other
     * additional meta data that need to be accessed for other reasons.The data
     * will be periodically sent to the eureka server.
     * 
     * @param appMetadata
     *            application specific meta data.
     */
    public void registerAppMetadata(Map<String, String> appMetadata) {
        instanceInfo.registerRuntimeMetadata(appMetadata);
    }

    /**
     * Set the status of this instance. Application can use this to indicate
     * whether it is ready to receive traffic.
     * 
     * @param status
     *            Status of the instance
     */
    public void setInstanceStatus(InstanceStatus status) {
        instanceInfo.setStatus(status);
    }

    /**
     * Refetches the hostname to check if it has changed. If it has, the entire
     * <code>DataCenterInfo</code> is refetched and passed on to the eureka
     * server on next heartbeat.
     */
    public void refreshDataCenterInfoIfRequired() {
        String existingHostname = instanceInfo.getHostName();
        String newHostname = config.getHostName(true);
        if (newHostname != null && !newHostname.equals(existingHostname)) {
            logger.warn("The public hostname changed from : "
                    + existingHostname + " => " + newHostname);
            InstanceInfo.Builder builder = new InstanceInfo.Builder(
                    instanceInfo);
            builder.setHostName(newHostname).setDataCenterInfo(
                    config.getDataCenterInfo());
            instanceInfo.setIsDirty(true);
        }
    }
}