/*
 * StatusInfo.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.config.NetflixConfiguration;
import com.netflix.logging.Counter;
import com.netflix.logging.dto.CountEntry;
import com.netflix.niws.IPayload;
import com.netflix.niws.PayloadConverter;
import com.netflix.pool.ResourcePool;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Container for exposing status information.
 * 
 * @author gkim
 */
@PayloadConverter("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("status")
public class StatusInfo implements IPayload {
    private final static String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z";
    
    public static final class Builder {
        
        @XStreamOmitField
        private StatusInfo result;
        
        private Builder() {
            result = new StatusInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }
        
        /**
         * Explicitly get whether application is "healthy", otherwise we'll
         * automatically infer health from LogManager's recent error count.
         */
        public Builder isHealthy(boolean b) {
            result.isHeathly = Boolean.valueOf(b);
            return this;
        }

        /**
         * Add any application specific status data
         */
        public Builder add(String key, String value){
            if(result.applicationStats == null) {
                result.applicationStats = new HashMap<String, String>();
            }
            result.applicationStats.put(key,value);
            return this;
        }
        
        /**
         * Build the {@link StatusInfo}
         * General information are automatically built here too.
         */
        @SuppressWarnings("unchecked")
        public StatusInfo build() {
            result.generalStats.put("server-uptime", getUpTime());
            result.generalStats.put("environment", NetflixConfiguration.getEnvironment());
            
            Runtime runtime = Runtime.getRuntime();
            int totalMem = (int) (runtime.totalMemory() / 1048576);
            int freeMem = (int) (runtime.freeMemory() / 1048576);
            int usedPercent = (int) (((float) totalMem - freeMem) / (totalMem) * 100.0);
   
            result.generalStats.put("num-of-cpus", String.valueOf(runtime.availableProcessors()));
            result.generalStats.put("total-avail-memory", String.valueOf(totalMem) + "mb");
            result.generalStats.put("current-memory-usage",
                     String.valueOf(totalMem-freeMem) + "mb" + " (" + usedPercent + "%)");
            List poolList = ResourcePool.getReportDataAll();
            for (int i = 0; i < poolList.size(); i++) {
                HashMap poolMap = (HashMap) poolList.get(i);
                result.generalStats.put("resource-pool-size:"+ poolMap.get("pool name"),
                        ((Integer)poolMap.get("size")).toString());
                result.generalStats.put("resource-pool-used:"+ poolMap.get("pool name"),
                        ((Integer)poolMap.get("used")).toString());
            }

            if(result.isHeathly == null){
                //then infer
                CountEntry errorCntEntry = Counter.getValue("Logging-level:ERROR");
                boolean healthy = true;
                if(errorCntEntry != null && errorCntEntry.getValue() > 20){
                    healthy = false;
                }
                result.isHeathly = Boolean.valueOf(healthy);
            }
            
            result.instanceInfo = ApplicationInfoManager.getInstance().getInfo();
            
            return result;
        }
    }
    
    private Map<String, String> generalStats = new HashMap<String, String>();
    private Map<String, String> applicationStats;
    private InstanceInfo instanceInfo;
    private Boolean isHeathly;
    
    private StatusInfo() {}
    
    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }
    
    public boolean isHealthy() {
        return isHeathly.booleanValue();
    }
    
    public Map<String, String> getGeneralStats() {
        return generalStats;
    }
    
    public Map<String, String> getApplicationStats() {
        return applicationStats;
    }
    
    /**
     * Output the amount of time that has elapsed since the given date in the
     * format x days, xx:xx.
     *
     * @return A string representing the formatted interval.
     */
    public static String getUpTime() {
        long diff = ManagementFactory.getRuntimeMXBean().getUptime();
        diff /= 1000 * 60;
        long minutes = diff % 60;
        diff /= 60;
        long hours = diff % 24;
        diff /= 24;
        long days = diff;
        StringBuilder buf = new StringBuilder();
        if (days == 1)
            buf.append("1 day ");
        else if (days > 1)
            buf.append(Long.valueOf(days).toString()).append(" days ");
        DecimalFormat format = new DecimalFormat();
        format.setMinimumIntegerDigits(2);
        buf.append(format.format(hours)).append(":").append(format.format(minutes));
        return buf.toString();
    }
    
    public static String getCurrentTimeAsString() {
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        return format.format(new Date());
    }
}
