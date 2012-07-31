/*
 * Application.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.shared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.niws.IPayload;
import com.netflix.niws.PayloadConverter;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Collection of instances running w/ this application
 * 
 * @author gkim
 */
@PayloadConverter("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("application")
public class Application implements IPayload {
    
    private String name;
    
    @XStreamOmitField
    private volatile boolean _isDirty = false;
    
    @XStreamImplicit
    private Set<InstanceInfo> instances;
   
    private Map<String, InstanceInfo> instancesMap;
  
    public Application() {
        instances = new LinkedHashSet<InstanceInfo>();
        instancesMap = new ConcurrentHashMap<String, InstanceInfo>();
   }
    
    public Application(String name) {
        this.name = name;
        instancesMap = new ConcurrentHashMap<String, InstanceInfo>();
        instances = new LinkedHashSet<InstanceInfo>();
    }
    
    public void addInstance(InstanceInfo i){
        instancesMap.put(i.getId(), i);
        synchronized (instances) {
            instances.remove(i);
            instances.add(i);
            _isDirty = true;
        }
    }
    
    public void removeInstance(InstanceInfo i){
        instancesMap.remove(i.getId());
        synchronized (instances) {
             instances.remove(i);
            _isDirty = true;
        }
    }
 
    /**
     * Always shuffle the list to return so that the first one doesn't get
     * hammered especially in bulk fills
     */
    public List<InstanceInfo> getInstances() {
        List<InstanceInfo> instanceInfoList = null;
        synchronized (instances) {
            instanceInfoList = new ArrayList<InstanceInfo>(instances);
        }
        Collections.shuffle(instanceInfoList);
        return instanceInfoList;
    }
    
    public InstanceInfo getByInstanceId(String id){
        return instancesMap.get(id);
    }
   
    public String getName() {
        return name;
    }
    
    public void setName(String name){
        this.name = name;
    }
    
}
