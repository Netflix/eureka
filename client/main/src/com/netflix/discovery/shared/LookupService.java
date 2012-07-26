/*
 * LookupService.java
 *  
 * $Header: //depot/commonlibraries/platform/cloud/src/com/netflix/discovery/LookupService.java#1 $ 
 * $DateTime: 2009/01/28 14:14:58 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.shared;

import java.util.Collections;
import java.util.List;

import com.netflix.appinfo.InstanceInfo;


/**
 * Lookup service for finding active instances
 * 
 * @author gkim
 */
public interface LookupService<T> {

  
    
    /**
     * Returns the corresponding {@link Application} object which is basically
     * a container of all registered <code>appName</code> {@link InstanceInfo}s.
     *
     * @param appName 
     * @return a {@link Application} or null
     *  if we couldn't locate any app of the requested appName
     */
    Application getApplication(String appName);
    
    /**
     * Returns the {@link Applications} object which is basically a container of
     * all currently registered {@link Application}s.
     * 
     * @param appName 
     * @return {@link Applications}
     */
    Applications getApplications();
    
    
    /**
     * Returns the {@link List} of {@link InstanceInfo}s matching the the passed in
     * id.  A single {@link InstanceInfo} can possibly be registered w/ more than one
     * {@link Application}s
     * 
     * @param id
     * @return {@link List} of {@link InstanceInfo}s or {@link Collections#emptyList()}
     */
    List<InstanceInfo> getInstancesById(String id);
    
}
