/*
 * ContainerContext.java
 *
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/ContainerContext.java#3 $
 * $DateTime: 2012/07/26 13:04:20 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.cluster.ReplicaNode;
import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.util.DSCounter;
import com.netflix.discovery.util.EIPManager;
import com.thoughtworks.xstream.XStream;

/**
 * Lifecycle code for our container
 *
 * @author gkim
 */
public class ContainerContext implements ServletContextListener {

    private static final int EIP_SLEEP_TIME_MS = 1000;
    private static final int EIPBIND_INTERVAL_MS = 5 * 60 * 1000;
    public static final String VERSION = "v2.0.4";
    private static final AtomicBoolean s_isInitialized = new AtomicBoolean(false);
    public static final String APP_NAME = "discovery";
    private static final Logger s_logger = LoggerFactory.getLogger(ContainerContext.class); 
    private static final Timer timer = new Timer();
    private static final DynamicIntProperty BIND_UNBIND_RETRIES = DynamicPropertyFactory.getInstance().getIntProperty("netflix.discovery.eipRetries", 3);
   
    /**
     * {@inheritDoc}
     */
    public void contextInitialized(ServletContextEvent event) {
        if (!s_isInitialized.get()) {

            String appName = event.getServletContext().getServletContextName();
        
            try{
                ConfigurationManager.loadPropertiesFromResources("discovery.properties");
                ApplicationInfoManager.getInstance().initComponent();
                DiscoveryManager.getInstance().initComponent();
                s_logger.info("=====================================================");
                s_logger.info("Environment: " + ConfigurationManager.getDeploymentContext().getDeploymentEnvironment());
                s_logger.info("DataCenter: " + ConfigurationManager.getDeploymentContext().getDeploymentDatacenter());
                s_logger.info("=====================================================");
      

                s_isInitialized.set(true);

                s_logger.info("Environment is: " + ConfigurationManager.getDeploymentContext().getDeploymentEnvironment());
               //Register V1 aware converter for InstanceInfo
                JsonXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(),
                        XStream.PRIORITY_VERY_HIGH);
                XmlXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(),
                        XStream.PRIORITY_VERY_HIGH);
                //Bind to EIP if needed
                for (int i=0; i < BIND_UNBIND_RETRIES.get(); i ++) {
                    try {
                        EIPManager.getInstance().bindToEIP();
                        break;
                    }
                    catch (Throwable e) {
                        Thread.sleep(EIP_SLEEP_TIME_MS);
                        continue;
                    }
                }
                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        try {
                            EIPManager.getInstance().bindToEIP();
                        } catch (Throwable e) {
                            s_logger.error("Cannot bind to the EIP:", e);
                        }

                    }
                }, EIPBIND_INTERVAL_MS, EIPBIND_INTERVAL_MS);

                //Register DSCounters w/ MonitorRegistry
                DSCounter.registerAllAsMBeans();
                
                ReplicaAwareInstanceRegistry registry = ReplicaAwareInstanceRegistry.getInstance();

                //Copy registry from neighboring DS node
                registry.syncUp();

                for(ReplicaNode node : registry.getReplicaNodes()){
                    s_logger.info("Replica node URL:  " +
                            node.getServiceUrl());
                }
            }catch(Exception e) {
                //Bomb
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void contextDestroyed(ServletContextEvent event) {
        if (s_isInitialized.get()) {

            try {
                s_logger.info(new Date().toString() + " Shutting down Discovery service..." );
                
                //Unregister all MBeans associated w/ DSCounters
                DSCounter.shutdown();
                for (int i=0; i < BIND_UNBIND_RETRIES.get(); i ++) {
                    try {
                        EIPManager.getInstance().unbindEIP();
                        break;
                    }
                    catch (Throwable e) {
                        Thread.sleep(1000);
                        continue;
                    }
                }
                ReplicaAwareInstanceRegistry.getInstance().shutdown();
                s_isInitialized.set(false);

                
            } catch (Exception e) {
                // log and proceed
                System.err.println("Discovery: Error cleaning resourcses : " + e.getMessage());
                e.printStackTrace(System.err);
            }
            s_logger.info(new Date().toString() + " Discovery service is now shutdown..." );
        }

    }
}
