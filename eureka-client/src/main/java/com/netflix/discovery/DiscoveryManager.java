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

package com.netflix.discovery;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient.DiscoveryClientOptionalArgs;
import com.netflix.discovery.shared.LookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @deprecated Please install the appropriate modules (e.g. {@link com.netflix.discovery.guice.EurekaModule}
 * and use dependency injection. Alternatively, create {@link com.netflix.appinfo.ApplicationInfoManager} and
 * {@link com.netflix.discovery.DiscoveryClient} directly.
 *
 * <tt>Discovery Manager</tt> configures <tt>Discovery Client</tt> based on the
 * properties specified.
 *
 * <p>
 * The configuration file is searched for in the classpath with the name
 * specified by the property <em>eureka.client.props</em> and with the suffix
 * <em>.properties</em>. If the property is not specified,
 * <em>eureka-client.properties</em> is assumed as the default.
 *
 * @author Karthik Ranganathan
 *
 */
@Deprecated
@Singleton
public class DiscoveryManager {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryManager.class);

    private static volatile DiscoveryManager INSTANCE;

    private volatile EurekaInstanceConfig eurekaInstanceConfig;
    private volatile EurekaClientConfig eurekaClientConfig;
    private volatile EurekaClient eurekaClient;

    private DiscoveryManager() {
        // static singleton hack
        INSTANCE = this;
    }

    @Inject
    DiscoveryManager(EurekaInstanceConfig eurekaInstanceConfig, EurekaClientConfig eurekaClientConfig, EurekaClient eurekaClient) {
        this.eurekaInstanceConfig = eurekaInstanceConfig;
        this.eurekaClientConfig = eurekaClientConfig;
        this.eurekaClient = eurekaClient;

        // static singleton hack
        INSTANCE = this;
    }

    public static synchronized DiscoveryManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DiscoveryManager();
        }
        return INSTANCE;
    }

    public synchronized void setDiscoveryClient(DiscoveryClient discoveryClient) {
        if (this.eurekaClient == null) {
            this.eurekaClient = discoveryClient;
        }
    }

    public synchronized void setEurekaClientConfig(EurekaClientConfig eurekaClientConfig) {
        if (this.eurekaClientConfig == null) {
            this.eurekaClientConfig = eurekaClientConfig;
        }
    }

    public synchronized void setEurekaInstanceConfig(EurekaInstanceConfig eurekaInstanceConfig) {
        if (this.eurekaInstanceConfig == null) {
            this.eurekaInstanceConfig = eurekaInstanceConfig;
        }
    }

    /**
     * Initializes the <tt>Discovery Client</tt> with the given configuration.
     *
     * @param config
     *            the instance info configuration that will be used for
     *            registration with Eureka.
     * @param eurekaConfig the eureka client configuration of the instance.
     */
    public synchronized void initComponent(EurekaInstanceConfig config,
                              EurekaClientConfig eurekaConfig, DiscoveryClientOptionalArgs args) {
        setEurekaInstanceConfig(config);
        setEurekaClientConfig(eurekaConfig);

        if (ApplicationInfoManager.getInstance().getInfo() == null) {
            // Initialize application info
            ApplicationInfoManager.getInstance().initComponent(config);
        }
        InstanceInfo info = ApplicationInfoManager.getInstance().getInfo();

        setDiscoveryClient(new DiscoveryClient(info, eurekaConfig, args));
    }
    
    public synchronized void initComponent(EurekaInstanceConfig config,
            EurekaClientConfig eurekaConfig) {
        initComponent(config, eurekaConfig, null);
    }

    /**
     * Shuts down the <tt>Discovery Client</tt> which unregisters the
     * information about this instance from the <tt>Discovery Server</tt>.
     */
    public void shutdownComponent() {
        if (eurekaClient != null) {
            try {
                eurekaClient.shutdown();
                eurekaClient = null;
            } catch (Throwable th) {
                logger.error("Error in shutting down client", th);
            }
        }
    }

    public LookupService getLookupService() {
        return eurekaClient;
    }

    /**
     * @deprecated use {@link #getEurekaClient()}
     *
     * Get the {@link DiscoveryClient}.
     * @return the client that is used to talk to eureka.
     */
    @Deprecated
    public DiscoveryClient getDiscoveryClient() {
        return (DiscoveryClient) eurekaClient;
    }

    /**
     *
     * Get the {@link EurekaClient} implementation.
     * @return the client that is used to talk to eureka.
     */
    public EurekaClient getEurekaClient() {
        return eurekaClient;
    }

    /**
     * Get the instance of {@link EurekaClientConfig} this instance was initialized with.
     * @return the instance of {@link EurekaClientConfig} this instance was initialized with.
     */
    public EurekaClientConfig getEurekaClientConfig() {
        return eurekaClientConfig;
    }

    /**
     * Get the instance of {@link EurekaInstanceConfig} this instance was initialized with.
     * @return the instance of {@link EurekaInstanceConfig} this instance was initialized with.
     */
    public EurekaInstanceConfig getEurekaInstanceConfig() {
        return eurekaInstanceConfig;
    }
}
