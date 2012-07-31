/*
 * DiscoveryManager.java
 *  
 * $Header: //depot/commonlibraries/eureka-client/main/src/com/netflix/discovery/DiscoveryManager.java#3 $ 
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2008 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.LookupService;

/**
 * Services that wish to be "discovered" will register w/ the {@link DiscoveryManager}.
 * Clients that wish to discover available services will use this to query for
 * available services.  Currently required in the Cloud environment, but can be
 * extended to also run anywhere else.
 * 
 * @author gkim
 * 
 */
public class DiscoveryManager  {

    public static final String NAME = "DISCOVERY";
    
    private volatile static DiscoveryManager s_instance = new DiscoveryManager();
    
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryManager.class);

    private DiscoveryClient _client;
    
    private DiscoveryManager() { }

    public static DiscoveryManager getInstance() {
        return s_instance;
    }
    
    /**
     * @see {@link IComponent#getName()}
     */
    public String getName() {
        return NAME;
    }

    /**
     * @see {@link IComponent#getProperties()}
     */
    public Properties getProperties() {
        // TODO Auto-generated method stub
        return null;
    }


    public void initComponent() {
      InstanceInfo info =  ApplicationInfoManager.getInstance().getInfo();
       try {
        ConfigurationManager.loadPropertiesFromResources("eureka-client.properties");
    } catch (IOException e) {
       logger.warn("Cannot find eureka-client.properties");
    }
        _client = new DiscoveryClient(info);
   
    }

    /**
     * @see {@link IComponent#shutdownComponent()}
     */
    public void shutdownComponent() {
      if(_client != null){
           // Do not throw an exception to platform, it affects the shutdown
           // of other components since platform exits without shutting down other components.
           try {
           _client.shutdown();
          _client = null;
           }
           catch (Throwable th) {
               logger.error("Error in shutting down client", th);
           }
       }
    }
    
    /**
     * Get the {@link LookupService}
     */
    public LookupService getLookupService() {
        return _client;
    }
    
    /**
     * Get the {@link DiscoveryClient}
     */
    public DiscoveryClient getDiscoveryClient() {
        return _client;
    }
    
    
}
