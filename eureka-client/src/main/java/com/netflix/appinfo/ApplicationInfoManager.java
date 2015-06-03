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

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.governator.guice.lazy.FineGrainedLazySingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@FineGrainedLazySingleton
public class ApplicationInfoManager {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationInfoManager.class);
    private static ApplicationInfoManager instance = new ApplicationInfoManager();

    private InstanceInfo instanceInfo;
    private EurekaInstanceConfig config;

    private Map<String, StatusChangeListener> listeners;

    private ApplicationInfoManager() {
        listeners = new ConcurrentHashMap<String, StatusChangeListener>();
    }

    @Inject
    ApplicationInfoManager(EurekaInstanceConfig config, InstanceInfo instanceInfo) {
        this.config = config;
        this.instanceInfo = instanceInfo;
        this.listeners = new ConcurrentHashMap<String, StatusChangeListener>();

        // Hack to allow for getInstance() to use the DI'd ApplicationInfoManager
        instance = this;
    }

    /**
     * @deprecated please use DI instead
     */
    @Deprecated
    public static ApplicationInfoManager getInstance() {
        return instance;
    }

    public void initComponent(EurekaInstanceConfig config) {
        try {
            this.config = config;
            this.instanceInfo = new EurekaConfigBasedInstanceInfoProvider(config).get();
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
     * whether it is ready to receive traffic. Setting the status here also notifies all registered listeners
     * of a status change event.
     *
     * @param status Status of the instance
     */
    public synchronized void setInstanceStatus(InstanceStatus status) {
        InstanceStatus prev = instanceInfo.setStatus(status);
        if (prev != null) {
            for (StatusChangeListener listener : listeners.values()) {
                try {
                    listener.notify(new StatusChangeEvent(prev, status));
                } catch (Exception e) {
                    logger.warn("failed to notify listener: {}", listener.getId(), e);
                }
            }
        }
    }

    public void registerStatusChangeListener(StatusChangeListener listener) {
        listeners.put(listener.getId(), listener);
    }

    public void unregisterStatusChangeListener(String listenerId) {
        listeners.remove(listenerId);
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
            instanceInfo.setIsDirty();
        }
    }

    public static interface StatusChangeListener {
        String getId();
        void notify(StatusChangeEvent statusChangeEvent);
    }
}
