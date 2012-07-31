/*
 * Lease.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/lease/Lease.java#2 $ 
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.lease;

import com.netflix.appinfo.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Describes a time-based availability of a {@link T}.  Purpose is to
 * avoid accumulation of outdated information as result of ungraceful shutdowns.
 * 
 * If a lease elapses without renewals, it will eventually expire consequently
 * marking the associated {@link T} for immediate eviction - this is 
 * similar to an explicit cancellation except that there is no communication between
 * the {@link T} and {@link LeaseManager}
 * 
 * @author gkim
 */
public class Lease<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Lease.class); 

    enum Action {Register, Cancel, Renew};
    
    public final static int DEFAULT_DURATION_IN_SECS = 90;
    
    private T _holder;
    private long _evictionTimestamp;
    private long _registrationTimestamp;
    private long lastStatusUpdateTimestamp;
    private long _lastUpdate;
    private long _duration;
    private volatile long _clock;
    
    public Lease(T r, int durationInSecs, long clock) {
        _holder = r;
        _clock = clock;
        _registrationTimestamp = System.currentTimeMillis();
        _lastUpdate = _registrationTimestamp;
        _duration = (durationInSecs*1000);
        
        if(_clock > 0) {
            LOGGER.warn("DS: clock: started at " + _clock);
        }
    }
    
    /**
     * Renew the lease, use renewal duration if it was specified by the associated 
     * {@link T} during registration, otherwise 
     * default duration is {@link DurationBasedLease#DEFAULT_RENEWAL_DURATION_IN_MS}
     */
    public void renew(long clock){
        _lastUpdate = System.currentTimeMillis() + _duration;
        if(_clock != clock && (_clock + 1) != clock) {
            String application = "UNKNOWN";
            String instanceId = "UNKNOWN";
            if ((_holder!=null) && (InstanceInfo.class.isInstance(_holder))) {
                application = ((InstanceInfo)_holder).getAppName();
                instanceId = ((InstanceInfo)_holder).getId();
            }
            
      }
        _clock = clock;
    }
    
    /**
     * Updates the eviction timestamp if not already set.
     */
    public void cancel(long clock){
        if(_evictionTimestamp <= 0) {
            _evictionTimestamp = System.currentTimeMillis();
        }
        if(clock > 0){
            _clock = clock;
        }
    }
    
    /**
     * Returns whether the lease has expired or not.
     */
    public boolean isExpired(){
        return (_evictionTimestamp > 0 ||
                System.currentTimeMillis() > (_lastUpdate + _duration));
    }
    
    public long getRegistrationTimestamp(){
        return _registrationTimestamp;
    }
    
    public long getLastRenewalTimestamp() {
        return _lastUpdate;
    }
    
    public long getEvictionTimestamp() {
        return _evictionTimestamp;
    }
    
    public long getClock() {
        return _clock;
    }
    
    /**
     * Returns the holder of the lease
     */
    public T getHolder() {
        return _holder;
    }

    public long getLastStatusUpdateTimestamp() {
        return lastStatusUpdateTimestamp;
    }

    public void setLastStatusUpdateTimestamp(long lastStatusUpdateTimestamp) {
        this.lastStatusUpdateTimestamp = lastStatusUpdateTimestamp;
    }
}
