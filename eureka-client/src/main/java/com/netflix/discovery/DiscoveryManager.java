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
import javax.inject.Provider;
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

    @Inject
    private volatile Provider<EurekaInstanceConfig> eurekaInstanceConfig = new Provider<EurekaInstanceConfig>() {
        @Override
        public EurekaInstanceConfig get() {
            return null;
        }
    };

    @Inject
    private volatile Provider<EurekaClientConfig> eurekaClientConfig = new Provider<EurekaClientConfig>() {
        @Override
        public EurekaClientConfig get() {
            return null;
        }
    };

    @Inject
    private volatile Provider<EurekaClient> eurekaClient = new Provider<EurekaClient>() {
        @Override
        public EurekaClient get() {
            return null;
        }
    };

    @Inject
    private DiscoveryManager() {
        // static singleton hack
        INSTANCE = this;
    }

    public static synchronized DiscoveryManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DiscoveryManager();
        }
        return INSTANCE;
    }

    public synchronized void setDiscoveryClient(final DiscoveryClient client) {
        if (this.eurekaClient.get() == null) {
            this.eurekaClient = new Provider<EurekaClient>() {
                @Override
                public EurekaClient get() {
                    return client;
                }
            };
        }
    }

    public synchronized void setEurekaClientConfig(final EurekaClientConfig config) {
        if (this.eurekaClientConfig.get() == null) {
            this.eurekaClientConfig = new Provider<EurekaClientConfig>() {
                @Override
                public EurekaClientConfig get() {
                    return config;
                }
            };
        }
    }

    public synchronized void setEurekaInstanceConfig(final EurekaInstanceConfig config) {
        if (this.eurekaInstanceConfig.get() == null) {
            this.eurekaInstanceConfig = new Provider<EurekaInstanceConfig>() {
                @Override
                public EurekaInstanceConfig get() {
                    return config;
                }
            };
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
        if (eurekaClient.get() != null) {
            try {
                eurekaClient.get().shutdown();
            } catch (Throwable th) {
                logger.error("Error in shutting down client", th);
            }
        }
    }

    public LookupService getLookupService() {
        return eurekaClient.get();
    }

    /**
     * @deprecated use {@link #getEurekaClient()}
     *
     * Get the {@link DiscoveryClient}.
     * @return the client that is used to talk to eureka.
     */
    @Deprecated
    public DiscoveryClient getDiscoveryClient() {
        return (DiscoveryClient) getEurekaClient();
    }

    /**
     *
     * Get the {@link EurekaClient} implementation.
     * @return the client that is used to talk to eureka.
     */
    public EurekaClient getEurekaClient() {
        return eurekaClient.get();
    }

    /**
     * Get the instance of {@link EurekaClientConfig} this instance was initialized with.
     * @return the instance of {@link EurekaClientConfig} this instance was initialized with.
     */
    public EurekaClientConfig getEurekaClientConfig() {
        return eurekaClientConfig.get();
    }

    /**
     * Get the instance of {@link EurekaInstanceConfig} this instance was initialized with.
     * @return the instance of {@link EurekaInstanceConfig} this instance was initialized with.
     */
    public EurekaInstanceConfig getEurekaInstanceConfig() {
        return eurekaInstanceConfig.get();
    }
}
