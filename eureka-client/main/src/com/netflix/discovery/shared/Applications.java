/*
 * Applications.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.shared;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.discovery.provider.Serializer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

/**
 * Collection of all applications
 * 
 * @author gkim
 */
@Serializer("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("applications")
public class Applications {
    private static final String APP_INSTANCEID_DELIMITER = "$$";

    private static final String STATUS_DELIMITER = "_";

    private Long version_delta = Long.valueOf(-1);
    
    @XStreamImplicit
    private AbstractQueue<Application> apps;
    
    private Map<String, Application> appsApplicationMap = new ConcurrentHashMap<String, Application>();
    
    private String appsHashCode;
    
    public Applications() {
        this.apps = new ConcurrentLinkedQueue<Application>();
    }
    
    public Applications(List<Application> apps) {
        this.apps = new ConcurrentLinkedQueue<Application>();
        this.apps.addAll(apps);
    }
    
    public void addApplication(Application app) {
        appsApplicationMap.put(app.getName().toUpperCase(), app);
        apps.add(app);
    }
    
    public List<Application> getRegisteredApplications() {
        List<Application> list = new ArrayList<Application>();
        list.addAll(this.apps);
        return list;
    }
    
    public Application getRegisteredApplications(String appName) {
        return appsApplicationMap.get(appName.toUpperCase());
    }
    
    public void setVersion(Long version) {
        this.version_delta = version;
    }
    
    public Long getVersion() {
        return this.version_delta;
    }
    
    public void setAppsHashCode(String hashCode) {
       this.appsHashCode = hashCode;
    }
    
    public String getAppsHashCode() {
        return this.appsHashCode;
     }
    
    public String getReconcileHashCode() {
        Map<String, AtomicInteger> instanceCountMap = new TreeMap<String, AtomicInteger>();
        for (Application app : this.getRegisteredApplications()) {
            for (InstanceInfo info : app.getInstances()) {
                AtomicInteger instanceCount = instanceCountMap.get(info
                        .getStatus().name());
                if (instanceCount == null) {
                    instanceCount = new AtomicInteger(0);
                    instanceCountMap
                    .put(info.getStatus().name(), instanceCount);
                }
                instanceCount.incrementAndGet();
            }
        }
        String reconcileHashCode = "";
        for (Map.Entry<String, AtomicInteger> mapEntry : instanceCountMap
                .entrySet()) {
            reconcileHashCode = reconcileHashCode + mapEntry.getKey() + STATUS_DELIMITER
            + mapEntry.getValue().get() + STATUS_DELIMITER;
        }
        return reconcileHashCode;
    }
    
    
    public Map<String, List<String>> getReconcileMapDiff(Applications apps) {
        Map<String, List<String>> diffMap = new TreeMap<String, List<String>>();
        Set<Pair> allInstanceAppInstanceIds = new HashSet<Pair>();
        for (Application otherApp : apps.getRegisteredApplications()) {
            Application thisApp = this.getRegisteredApplications(otherApp
                    .getName());
            for (InstanceInfo instanceInfo : thisApp.getInstances()) {
                allInstanceAppInstanceIds.add(new Pair(thisApp.getName(), instanceInfo.getId()));
            }
            for (InstanceInfo otherInstanceInfo : otherApp.getInstances()) {
                InstanceInfo thisInstanceInfo = thisApp
                        .getByInstanceId(otherInstanceInfo.getId());
                if (thisInstanceInfo == null) {
                    List<String> diffList = diffMap.get(ActionType.DELETED
                            .name());
                    if (diffList == null) {
                        diffList = new ArrayList<String>();
                        diffMap.put(ActionType.DELETED.name(), diffList);
                    }
                    diffList.add(otherInstanceInfo.getId());
                } else if (!thisInstanceInfo.getStatus().name()
                        .equalsIgnoreCase(otherInstanceInfo.getStatus().name())) {
                    List<String> diffList = diffMap.get(ActionType.MODIFIED
                            .name());
                    if (diffList == null) {
                        diffList = new ArrayList<String>();
                        diffMap.put(ActionType.MODIFIED.name(), diffList);
                    }
                    diffList.add(thisInstanceInfo.getId() + APP_INSTANCEID_DELIMITER
                            + thisInstanceInfo.getStatus().name() + APP_INSTANCEID_DELIMITER
                            + otherInstanceInfo.getStatus().name());
                }
                allInstanceAppInstanceIds.remove(new Pair(otherApp.getName(), otherInstanceInfo.getId()));
            }
        }
        for (Pair pair : allInstanceAppInstanceIds) {
            Application app = new Application(pair.getItem_1());
            InstanceInfo thisInstanceInfo = app.getByInstanceId(pair.getItem_2());
            if (thisInstanceInfo != null) {
                List<String> diffList = diffMap.get(ActionType.ADDED.name());
                if (diffList == null) {
                    diffList = new ArrayList<String>();
                    diffMap.put(ActionType.ADDED.name(), diffList);
                }
                diffList.add(thisInstanceInfo.getId());
            }
        }
        return diffMap;

    }
    
    private static final class Pair {
        private String item_1;
        private String item_2;
        
        
   
        public Pair(String item_1, String item_2) {
            super();
            this.item_1 = item_1;
            this.item_2 = item_2;
        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((item_1 == null) ? 0 : item_1.hashCode());
            result = prime * result
                    + ((item_2 == null) ? 0 : item_2.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Pair other = (Pair) obj;
            if (item_1 == null) {
                if (other.item_1 != null)
                    return false;
            } else if (!item_1.equals(other.item_1))
                return false;
            if (item_2 == null) {
                if (other.item_2 != null)
                    return false;
            } else if (!item_2.equals(other.item_2))
                return false;
            return true;
        }
        public String getItem_1() {
            return item_1;
        }
        public void setItem_1(String item_1) {
            this.item_1 = item_1;
        }
        public String getItem_2() {
            return item_2;
        }
        public void setItem_2(String item_2) {
            this.item_2 = item_2;
        }
    }
    
}
