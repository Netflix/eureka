/*
 * HealthCheckCallback.java
 *  
 * $Header: //depot/commonlibraries/eureka-client/main/src/com/netfix/appinfo/HealthCheckCallback.java#1 $ 
 * $DateTime: 2012/07/16 11:58:15 $
 *
 * Copyright (c) 2010 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import com.netflix.discovery.DiscoveryClient;

/**
 * Applications can implement this interface and register a callback w/ the 
 * {@link DiscoveryClient#registerHealthCheckCallback(HealthCheckCallback)}.  Your 
 * callback will be invoked every 30 secs;  if the instance is in 
 * {@link InstanceInfo.InstanceStatus#STARTING} status, we will delay the callback 
 * until the status changes.
 * 
 * Returning a false to the checkHealth() method will mark the instance DOWN with
 * discovery.
 */
public interface HealthCheckCallback {
    /**
     * If false, the instance will be marked DOWN with discovery.  If the instance
     * was already marked  {@link InstanceInfo.InstanceStatus#DOWN} , returning true 
     * here will mark the instance back {@link InstanceInfo.InstanceStatus#UP} 
     */
    boolean isHealthy();
}
