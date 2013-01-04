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

package com.netflix.eureka;

import java.net.URL;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.CloudInstanceConfig;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.blitz4j.LoggingConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.util.EIPManager;
import com.netflix.eureka.util.EurekaMonitors;
import com.thoughtworks.xstream.XStream;

/**
 * The class that kick starts the eureka server.
 * 
 * <p>
 * The eureka server is configured by using the configuration
 * {@link EurekaServerConfig} specified by <em>eureka.server.props</em> in the
 * classpath.The eureka client component is also initialized by using the
 * configuration {@link EurekaInstanceConfig} specified by
 * <em>eureka.client.props</em>. If the server runs in the AWS cloud, the eurea
 * server binds it to the elastic ip as specified.
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
public class EurekaBootStrap implements ServletContextListener {
    private static final String TEST = "test";

    private static final String ARCHAIUS_DEPLOYMENT_ENVIRONMENT = "archaius.deployment.environment";

    private static final String EUREKA_ENVIRONMENT = "eureka.environment";

    private static final String CLOUD = "cloud";
    private static final String DEFAULT = "default";

    private static final String ARCHAIUS_DEPLOYMENT_DATACENTER = "archaius.deployment.datacenter";

    private static final String EUREKA_DATACENTER = "eureka.datacenter";

    private static final Logger logger = LoggerFactory
    .getLogger(EurekaBootStrap.class);

    private static final int EIP_BIND_SLEEP_TIME_MS = 1000;
    private static final Timer timer = new Timer("Eureka-EIPBinder", true);

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.servlet.ServletContextListener#contextInitialized(javax.servlet
     * .ServletContextEvent)
     */
    public void contextInitialized(ServletContextEvent event) {
        try {
            initEurekaEnvironment();
          
            // For backward compatibility
            JsonXStream.getInstance().registerConverter(
                    new V1AwareInstanceInfoConverter(),
                    XStream.PRIORITY_VERY_HIGH);
            XmlXStream.getInstance().registerConverter(
                    new V1AwareInstanceInfoConverter(),
                    XStream.PRIORITY_VERY_HIGH);
            InstanceInfo info = ApplicationInfoManager.getInstance().getInfo();
         
            PeerAwareInstanceRegistry registry = PeerAwareInstanceRegistry
            .getInstance();

            // Copy registry from neighboring eureka node
            int registryCount = registry.syncUp();
            registry.openForTraffic(registryCount);

            // Only in AWS, enable the binding functionality
            if (Name.Amazon.equals(info.getDataCenterInfo().getName())) {
                handleEIPbinding();
            }
            // Initialize available remote registry
            PeerAwareInstanceRegistry.getInstance().initRemoteRegionRegistry();
            // Register all monitoring statistics.
            EurekaMonitors.registerAllStats();

            for (PeerEurekaNode node : registry.getReplicaNodes()) {
                logger.info("Replica node URL:  " + node.getServiceUrl());
            }

        } catch (Throwable e) {
            logger.error("Cannot bootstrap eureka server :", e);
            throw new RuntimeException("Cannot bootstrap eureka server :", e);
        }
    }

    /**
     * Users can override to initialize the environment themselves.
     */
    protected void initEurekaEnvironment() {
        logger.info("Setting the eureka configuration..");
        LoggingConfiguration.getInstance().configure();
        EurekaServerConfig eurekaServerConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(
                eurekaServerConfig);
   
        String dataCenter = ConfigurationManager.getConfigInstance()
        .getString(EUREKA_DATACENTER);
        if (dataCenter == null) {
            logger.info("Eureka data center value eureka.datacenter is not set, defaulting to default");
            ConfigurationManager.getConfigInstance().setProperty(
                    ARCHAIUS_DEPLOYMENT_DATACENTER, DEFAULT);
        } else {
            ConfigurationManager.getConfigInstance().setProperty(
                    ARCHAIUS_DEPLOYMENT_DATACENTER, dataCenter);
        }
        String environment = ConfigurationManager.getConfigInstance()
        .getString(EUREKA_ENVIRONMENT);
        if (environment == null) {
            ConfigurationManager.getConfigInstance().setProperty(
                    ARCHAIUS_DEPLOYMENT_ENVIRONMENT, TEST);
            logger.info("Eureka environment value eureka.environment is not set, defaulting to test");
        }
        EurekaInstanceConfig config;
        if (CLOUD.equals(ConfigurationManager.getDeploymentContext()
                .getDeploymentDatacenter())) {
            config = new CloudInstanceConfig();
        } else {
            config = new MyDataCenterInstanceConfig();
        }
        logger.info("Initializing the eureka client...");

        DiscoveryManager.getInstance().initComponent(config,
                new DefaultEurekaClientConfig());
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.
     * ServletContextEvent)
     */
    public void contextDestroyed(ServletContextEvent event) {
        try {
            logger.info(new Date().toString()
                    + " Shutting down Eureka Server..");
            InstanceInfo info = ApplicationInfoManager.getInstance().getInfo();
            // Unregister all MBeans associated w/ DSCounters
            EurekaMonitors.shutdown();
            for (int i = 0; i < EurekaServerConfigurationManager.getInstance()
            .getConfiguration().getEIPBindRebindRetries(); i++) {
                try {
                    if (Name.Amazon.equals(info.getDataCenterInfo().getName())) {
                        EIPManager.getInstance().unbindEIP();
                    }
                    break;
                } catch (Throwable e) {
                    logger.warn("Cannot unbind the EIP from the instance");
                    Thread.sleep(1000);
                    continue;
                }
            }
            PeerAwareInstanceRegistry.getInstance().shutdown();
            destroyEurekaEnvironment();

        } catch (Throwable e) {
            logger.error("Error shutting down eureka", e);
        }
        logger.info(new Date().toString()
                + " Eureka Service is now shutdown...");
    }

    /**
     * Users can override to clean up the environment themselves.
     */
    protected void destroyEurekaEnvironment() {
        
    }

    /**
     * Handles EIP binding process in AWS Cloud.
     * 
     * @throws InterruptedException
     */
    private void handleEIPbinding()
    throws InterruptedException {
        EurekaServerConfig eurekaServerConfig = EurekaServerConfigurationManager.getInstance().getConfiguration();
        int retries = eurekaServerConfig.getEIPBindRebindRetries();
        // Bind to EIP if needed
        for (int i = 0; i < retries; i++) {
            if (bindEIP()) {
                break;
            }
        }
        // Schedule a timer which periodically checks for EIP binding.
        scheduleEIPBindTask(eurekaServerConfig);
    }

    /**
     * Schedules a EIP binding timer task which constantly polls for EIP in the
     * same zone and binds it to itself.If the EIP is taken away for some
     * reason, this task tries to get the EIP back. Hence it is advised to take
     * one EIP assignment per instance in a zone.
     * 
     * @param eurekaServerConfig
     *            the Eureka Server Configuration.
     */
    private void scheduleEIPBindTask(
            EurekaServerConfig eurekaServerConfig) {
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                try {
                    bindEIP();
                } catch (Throwable ignore) {

                }
            }
        }, eurekaServerConfig.getEIPBindingRetryIntervalMs(),
        eurekaServerConfig.getEIPBindingRetryIntervalMs());
    }

    /**
     * Binds the EIP if it is not already bound.
     * 
     * @return
     * @throws InterruptedException
     */
    private boolean bindEIP() throws InterruptedException {
        try {
            EIPManager.getInstance().bindToEIP();
            return true;
        } catch (Throwable e) {
            logger.error("Cannot bind to EIP", e);
            Thread.sleep(EIP_BIND_SLEEP_TIME_MS);
            return false;
        }
    }

}
