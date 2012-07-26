/*
 * DSCounter.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/util/DSCounter.java#2 $ 
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.util;

import java.util.concurrent.atomic.AtomicLong;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;

/**
 * enum of all interesting counters to export as Counters and MBeans.  Exporting
 * as MBeans is useful for trending on both appdynamics + epic.
 * 
 * @author gkim
 */
public enum DSCounter {
    RENEW("renewCounter","Number of total renews seen since startup"),
    CANCEL("cancelCounter","Number of total cancels seen since startup"),
    GET_ALL_CACHE_MISS("getAllCacheMissCounter", "Number of total registery queries seen since startup"),
    GET_ALL_CACHE_MISS_DELTA("getAllCacheMissDeltaCounter", "Number of total registery queries for deltaseen since startup"),
    GET_ALL_DELTA("getAllDeltaCounter", "Number of total deltas since startup"),
    GET_ALL("getAllCounter", "Number of total registry queries seen since startup"),
    REGISTER("registerCounter","Number of total registers seen since startup"),
    EXPIRED("expiredCounter", "Number of total expired leases since startup"),
    STATUS_UPDATE("statusUpdateCounter", "Number of total admin status updates since startup"),
    CANCEL_NOT_FOUND("cancelNotFoundCounter", "Number of total cancel requests on non-existing instance since startup"),
    RENEW_NOT_FOUND("renewNotFoundexpiredCounter", "Number of total renew on non-existing instance since startup"),
    REJECTED_REPLICATIONS("numOfRejectedReplications", "Number of replications rejected because of full queue"),
    FAILED_REPLICATIONS("numOfFailedReplications", "Number of failed replications - likely from timeouts");
    
    private DSCounter(String name, String description){
        _counterName = name;
        _description = description;
        
        DataCenterInfo dcInfo =
            ApplicationInfoManager.getInstance().getInfo().getDataCenterInfo();
        if(dcInfo.getName() == Name.Amazon){
            _myZoneCounterName = ((AmazonInfo)dcInfo).get(MetaDataKey.availabilityZone)
                + "." + _counterName;
        }else {
            _myZoneCounterName = "dcmaster." + _counterName; 
        }
    }
    
   
    private final String _counterName;
    
    //Counter name to keep track of client driven activity (i.e. does not include
    //counts incremented by result of replication)
    private final String _myZoneCounterName;

    private final String _description;

    @com.netflix.servo.annotations.Monitor(name = "count", type = DataSourceType.COUNTER)
    private final AtomicLong _counter = new AtomicLong();
    
    @com.netflix.servo.annotations.Monitor(name = "count-minus-replication", type = DataSourceType.COUNTER)
    private final AtomicLong _myZoneCounter = new AtomicLong();
    
    public void increment() {
        increment(false);
    }
    
    public void increment(boolean isReplication) {
        _counter.incrementAndGet();
        Monitors.newCounter(_counterName).increment();
        
        if(! isReplication){
            _myZoneCounter.incrementAndGet();
            Monitors.newCounter(_myZoneCounterName).increment();
        }
    }
    
    public String getName() { return _counterName; }
    
    public String getZoneSpecificName() { return _myZoneCounterName; }
    
    public String getDescription() { return _description; }
    
    /**
     * @return returns overall count (includes those that resulted
     *         from replication)
     */
    public long getCount() { return _counter.get(); }
    
    /**
     * @return returns counts which resulted from actual clients (does not
     *         include those which resulted from replication)
     */
    public long getZoneSpecificCount() { return _myZoneCounter.get(); }
    

    public static void registerAllAsMBeans() {
        for (DSCounter c : DSCounter.values()) {
            DefaultMonitorRegistry.getInstance().register(Monitors.newObjectMonitor(c.name(), c));
        }
    }

    public static void shutdown() {
        for (DSCounter c : DSCounter.values()) {
            DefaultMonitorRegistry.getInstance().unregister(Monitors.newObjectMonitor(c.name(), c));
        }
    }
}
