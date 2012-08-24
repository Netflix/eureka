/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.discovery.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.provider.Serializer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * The application class holds the list of instances for a particular
 * application.
 * 
 * @author Karthik Ranganathan
 * 
 */
@Serializer("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("application")
public class Application {

    private String name;

    @XStreamOmitField
    private volatile boolean isDirty = false;

    @XStreamImplicit
    private Set<InstanceInfo> instances;

    private AtomicReference<List<InstanceInfo>> shuffledInstances = new AtomicReference<List<InstanceInfo>>();

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

    /**
     * Add the given instance info the list.
     * 
     * @param i
     *            the instance info object to be added.
     */
    public void addInstance(InstanceInfo i) {
        instancesMap.put(i.getId(), i);
        synchronized (instances) {
            instances.remove(i);
            instances.add(i);
            isDirty = true;
        }
    }

    /**
     * Remove the given instance info the list.
     * 
     * @param i
     *            the instance info object to be removed.
     */
    public void removeInstance(InstanceInfo i) {
        instancesMap.remove(i.getId());
        synchronized (instances) {
            instances.remove(i);
            isDirty = true;
        }
    }

    /**
     * Gets the list of instances associated with this particular application.
     * <p>
     * Note that the instances are always returned with random order after
     * shuffling to avoid traffic to the same instances during startup. The
     * shuffling always happens once after every fetch cycle as specified in
     * {@link EurekaClientConfig#getRegistryFetchIntervalSeconds}.
     * </p>
     * 
     * @return the list of shuffled instances associated with this application.
     */
    public List<InstanceInfo> getInstances() {
        if (this.shuffledInstances.get() == null) {
            return this.getInstancesAsIsFromEureka();
        }
        else {
            return this.shuffledInstances.get();
        }
    }
    
    /**
     * Gets the list of non-shuffled and non-filtered instances associated with this particular
     * application.
     * 
     * @return list of non-shuffled and non-filtered instances associated with this particular
     *         application.
     */
    public List<InstanceInfo> getInstancesAsIsFromEureka() {
        return new ArrayList<InstanceInfo>(this.instances);
    }

   
    /**
     * Get the instance info that matches the given id.
     * 
     * @param id
     *            the id for which the instance info needs to be returned.
     * @return the instance info object.
     */
    public InstanceInfo getByInstanceId(String id) {
        return instancesMap.get(id);
    }

    /**
     * Gets the name of the application.
     * 
     * @return the name of the application.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the application.
     * 
     * @param name
     *            the name of the application.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Shuffles the list of instances in the application and stores it for
     * future retrievals.
     * 
     * @param filterUpInstances
     *            indicates whether only the instances with status
     *            {@link InstanceStatus#UP} needs to be stored.
     */
    public void shuffleAndStoreInstances(boolean filterUpInstances) {
        List<InstanceInfo> instanceInfoList = null;
        synchronized (instances) {
            instanceInfoList = new ArrayList<InstanceInfo>(instances);
        }
        if (filterUpInstances) {
            Iterator<InstanceInfo> it = instanceInfoList.iterator();
            while (it.hasNext()) {
                InstanceInfo instanceInfo = it.next();
                if (!InstanceStatus.UP.equals(instanceInfo.getStatus())) {
                    it.remove();
                }
            }

        }
        Collections.shuffle(instanceInfoList);
        this.shuffledInstances.set(instanceInfoList);
    }
    
    
}
