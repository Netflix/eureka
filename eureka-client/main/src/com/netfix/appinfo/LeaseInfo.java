/*
 * LeaseInfo.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import java.util.Date;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Lease information populated by discovery service
 * 
 * @author gkim
 */
public class LeaseInfo {

    public static final class Builder {

        @XStreamOmitField
        private LeaseInfo result;

        private Builder() {
            result = new LeaseInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Sets the registration timestamp
         */
        public Builder setRegistrationTimestamp(long ts){
            result.registrationTimestamp = ts;
            return this;
        }

        /**
         * Sets the last renewal timestamp of lease
         */
        public Builder setRenewalTimestamp(long ts){
            result.lastRenewalTimestamp = ts;
            return this;
        }

        /**
         * Sets the de-registration timestamp
         */
        public Builder setEvictionTimestamp(long ts){
            result.evictionTimestamp = ts;
            return this;
        }
        
        /**
         * Sets the client specified setting for eviction (e.g. how long to wait
         * w/o renewal event)
         */
        public Builder setDurationInSecs(int d){
            if(d <= 0 ){
                result.durationInSecs = DEFAULT_LEASE_DURATION;
            }else {
                result.durationInSecs = d;
            }
            return this;
        }
        
        /**
         * Sets the client specified setting for renew interval
         */
        public Builder setRenewalIntervalInSecs(int i){
            if(i <= 0 ){
                result.renewalIntervalInSecs = DEFAULT_LEASE_RENEWAL_INTERVAL;
            }else {
                result.renewalIntervalInSecs = i;
            }
            return this;
        }
        
        /**
         * Sets the logical clock
         */
        public Builder setClock(long clock){
            result.clock = clock;
            return this;
        }

        /**
         * Build the {@link InstanceInfo}
         */
        public LeaseInfo build() {
            return result;
        }
    }

    public static final int DEFAULT_LEASE_RENEWAL_INTERVAL = 30;
    public static final int DEFAULT_LEASE_DURATION = 90;
    
    //Client settings
    private int renewalIntervalInSecs = DEFAULT_LEASE_RENEWAL_INTERVAL;
    private int durationInSecs = DEFAULT_LEASE_DURATION;
    
    //Server populated
    private long registrationTimestamp;
    private long lastRenewalTimestamp;
    private long evictionTimestamp;
    private long clock;

    private LeaseInfo() {}
    
    /**
     * Returns the registration timestamp
     */
    public long getRegistrationTimestamp(){
        return registrationTimestamp;
    }

    /**
     * Returns the last renewal timestamp of lease
     */
    public long getRenewalTimestamp(){
        return lastRenewalTimestamp;
    }

    /**
     * Returns the de-registration timestamp
     */
    public long getEvictionTimestamp(){
        return evictionTimestamp;
    }
    
    /**
     * Returns client specified setting for renew interval
     */
    public int getRenewalIntervalInSecs() {
        return renewalIntervalInSecs;
    }
    
    /**
     * Returns client specified setting for eviction (e.g. how long to wait
     * w/o renewal event)
     */
    public int getDurationInSecs() {
        return durationInSecs;
    }
    
    /**
     * Returns the logical clock
     */
    public long getClock(){
        return clock;
    }
    
    public String toString() {
        StringBuilder buf = new StringBuilder("[Lease Info:\n");
        buf.append("\tregistrationTimestamp: ").append(registrationTimestamp > 0 ? 
                new Date(registrationTimestamp) : "None").
            append("\n\tlastRenewalTimestamp: ").append(lastRenewalTimestamp > 0 ?
                new Date(lastRenewalTimestamp) : "None").
            append("\n\tevictionTimestamp: ").append(evictionTimestamp > 0 ?
                new Date(evictionTimestamp) : "None").
            append("\n\tclock: ").append(clock);
     
        buf.append("\n]");
        return buf.toString();
    }
}