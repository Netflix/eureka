/*
 * LeaseManager.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/lease/LeaseManager.java#1 $ 
 * $DateTime: 2012/06/18 13:12:02 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.lease;


/**
 * Grants a lease to a {@link T}.  Also responsible for evicting
 * {@link T}s w/ expired {@link Lease)s.   
 * 
 * @author gkim
 */
public interface LeaseManager<T> {

    /**
     * Assign a new {@link Lease} to the passed in {@link T}
     * 
     * @param r - T to register
     * @param leaseDuration - duration of lease in seconds
     * @param clock - clock value from client
     * @param isReplication - whether this is a replicated entry from another ds node
     */
    void register(T r, int leaseDuration, long clock, boolean isReplication);
    
    /**
     * Cancel the {@link Lease} associated w/ the passed in <code>appName</code>
     * and <code>id</code>
     * 
     * @param appName - well known appName
     * @param id - unique id within appName
     * @param clock - clock value from client
     * @param isReplication - whether this is a replicated entry from another ds node
     * @return whether the operation of successful
     */
    boolean cancel(String appName, String id, long clock, boolean isReplication);
    
    /**
     * Renew the {@link Lease} associated w/ the passed in <code>appName</code>
     * and <code>id</code>
     *
     * @param id - unique id within appName
     * @param clock - clock value from client
     * @param isReplication - whether this is a replicated entry from another ds node
     * @return whether the operation of successful
     */
    boolean renew(String appName, String id, long clock, boolean isReplication);
    
    /**
     * Evict {@link T}s w/ expired {@link Lease)s
     */    
    void evict();
}

