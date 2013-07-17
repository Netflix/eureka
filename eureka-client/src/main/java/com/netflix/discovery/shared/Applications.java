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

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.InstanceRegionChecker;
import com.sun.jersey.api.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.provider.Serializer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import javax.annotation.Nullable;

/**
 * The class that wraps all the registry information returned by eureka server.
 * 
 * <p>
 * Note that the registry information is fetched from eureka server as specified
 * in {@link EurekaClientConfig#getRegistryFetchIntervalSeconds()}.Once the
 * information is fetched it is shuffled and also filtered for instances with
 * {@link InstanceStatus#UP} status as specified by the configuration
 * {@link EurekaClientConfig#shouldFilterOnlyUpInstances()}.
 * </p>
 * 
 * @author Karthik Ranganathan
 * 
 */
@Serializer("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("applications")
public class Applications {
    private static final String APP_INSTANCEID_DELIMITER = "$$";
    private static final Logger logger = LoggerFactory.getLogger(Applications.class);
    private static final String STATUS_DELIMITER = "_";

    private Long version_delta = Long.valueOf(-1);
  
    @XStreamImplicit
    private AbstractQueue<Application> applications;

    private Map<String, Application> appNameApplicationMap = new ConcurrentHashMap<String, Application>();
    private Map<String, AbstractQueue<InstanceInfo>> virtualHostNameAppMap = new ConcurrentHashMap<String, AbstractQueue<InstanceInfo>>();
    private Map<String, AbstractQueue<InstanceInfo>> secureVirtualHostNameAppMap = new ConcurrentHashMap<String, AbstractQueue<InstanceInfo>>();
    private Map<String, AtomicLong> virtualHostNameIndexMap = new ConcurrentHashMap<String, AtomicLong>();
    private Map<String, AtomicLong> secureVirtualHostNameIndexMap = new ConcurrentHashMap<String, AtomicLong>();

    private Map<String, AtomicReference<List<InstanceInfo>>> shuffleVirtualHostNameMap = new ConcurrentHashMap<String, AtomicReference<List<InstanceInfo>>>();
    private Map<String, AtomicReference<List<InstanceInfo>>> shuffledSecureVirtualHostNameMap = new ConcurrentHashMap<String, AtomicReference<List<InstanceInfo>>>();

    private String appsHashCode;

    public Applications() {
        this.applications = new ConcurrentLinkedQueue<Application>();
    }

    public Applications(List<Application> apps) {
        this.applications = new ConcurrentLinkedQueue<Application>();
        this.applications.addAll(apps);
    }

    /**
     * Add the <em>application</em> to the list.
     * 
     * @param app
     *            the <em>application</em> to be added.
     */
    public void addApplication(Application app) {
        appNameApplicationMap.put(app.getName().toUpperCase(), app);
        addInstancesToVIPMaps(app);
        applications.add(app);
    }

   
    /**
     * Gets the list of all registered <em>applications</em> from eureka.
     * 
     * @return list containing all applications registered with eureka.
     */
    public List<Application> getRegisteredApplications() {
        List<Application> list = new ArrayList<Application>();
        list.addAll(this.applications);
        return list;
    }

    /**
     * Gets the list of all registered <em>applications</em> for the given
     * application name.
     * 
     * @param appName
     *            the application name for which the result need to be fetched.
     * @return the list of registered applications for the given application
     *         name.
     */
    public Application getRegisteredApplications(String appName) {
        return appNameApplicationMap.get(appName.toUpperCase());
    }

    /**
     * Gets the list of <em>instances</em> associated to a virtual host name.
     * 
     * @param virtualHostName
     *            the virtual hostname for which the instances need to be
     *            returned.
     * @return list of <em>instances</em>.
     */
    public List<InstanceInfo> getInstancesByVirtualHostName(
            String virtualHostName) {
        AtomicReference<List<InstanceInfo>> ref = this.shuffleVirtualHostNameMap
        .get(virtualHostName.toUpperCase());
        if (ref == null || ref.get() == null) {
            return new ArrayList<InstanceInfo>();
        } else {
            return ref.get();
        }
    }

    /**
     * Gets the list of secure <em>instances</em> associated to a virtual host
     * name.
     * 
     * @param secureVirtualHostName
     *            the virtual hostname for which the secure instances need to be
     *            returned.
     * @return list of <em>instances</em>.
     */
    public List<InstanceInfo> getInstancesBySecureVirtualHostName(
            String secureVirtualHostName) {
        AtomicReference<List<InstanceInfo>> ref = this.shuffledSecureVirtualHostNameMap
        .get(secureVirtualHostName.toUpperCase());
        if (ref == null || ref.get() == null) {
            return new ArrayList<InstanceInfo>();
        } else {
            return ref.get();
        }
    }

    @Deprecated
    public void setVersion(Long version) {
        this.version_delta = version;
    }

    @Deprecated
    public Long getVersion() {
        return this.version_delta;
    }

    /**
     * Used by the eureka server. Not for external use.
     * 
     * @param hashCode
     */
    public void setAppsHashCode(String hashCode) {
        this.appsHashCode = hashCode;
    }

    /**
     * Used by the eureka server. Not for external use.
     * @return the string indicating the hashcode based on the applications stored.
     *
     */
    public String getAppsHashCode() {
        return this.appsHashCode;
    }

    /**
     * Gets the hash code for this <em>applications</em> instance. Used for
     * comparison of instances between eureka server and eureka client.
     * 
     * @return the internal hash code representation indicating the information
     *         about the instances.
     */
    public String getReconcileHashCode() {
        TreeMap<String, AtomicInteger> instanceCountMap = new TreeMap<String, AtomicInteger>();
        populateInstanceCountMap(instanceCountMap);
        return getReconcileHashCode(instanceCountMap);
    }

    public void populateInstanceCountMap(TreeMap<String, AtomicInteger> instanceCountMap) {
        for (Application app : this.getRegisteredApplications()) {
            for (InstanceInfo info : app.getInstancesAsIsFromEureka()) {
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
    }

    public static String getReconcileHashCode(TreeMap<String, AtomicInteger> instanceCountMap) {
        String reconcileHashCode = "";
        for (Map.Entry<String, AtomicInteger> mapEntry : instanceCountMap
                .entrySet()) {
            reconcileHashCode = reconcileHashCode + mapEntry.getKey()
            + STATUS_DELIMITER + mapEntry.getValue().get()
            + STATUS_DELIMITER;
        }
        return reconcileHashCode;
    }

    /**
     * Gets the exact difference between this applications instance and another
     * one.
     * 
     * @param apps
     *            the applications for which to compare this one.
     * @return a map containing the differences between the two.
     */
    public Map<String, List<String>> getReconcileMapDiff(Applications apps) {
        Map<String, List<String>> diffMap = new TreeMap<String, List<String>>();
        Set<Pair> allInstanceAppInstanceIds = new HashSet<Pair>();
        for (Application otherApp : apps.getRegisteredApplications()) {
            Application thisApp = this.getRegisteredApplications(otherApp
                    .getName());
            if (thisApp == null) {
                logger.warn("The application %s is not found in local cache :", otherApp.getName());
                continue;
            }
            for (InstanceInfo instanceInfo : thisApp.getInstancesAsIsFromEureka()) {
                allInstanceAppInstanceIds.add(new Pair(thisApp.getName(),
                        instanceInfo.getId()));
            }
            for (InstanceInfo otherInstanceInfo : otherApp.getInstancesAsIsFromEureka()) {
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
                    diffList.add(thisInstanceInfo.getId()
                            + APP_INSTANCEID_DELIMITER
                            + thisInstanceInfo.getStatus().name()
                            + APP_INSTANCEID_DELIMITER
                            + otherInstanceInfo.getStatus().name());
                }
                allInstanceAppInstanceIds.remove(new Pair(otherApp.getName(),
                        otherInstanceInfo.getId()));
            }
        }
        for (Pair pair : allInstanceAppInstanceIds) {
            Application app = new Application(pair.getItem_1());
            InstanceInfo thisInstanceInfo = app.getByInstanceId(pair
                    .getItem_2());
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

    public void shuffleInstances(boolean filterUpInstances) {
        _shuffleInstances(filterUpInstances, false, null, null, null);
    }

    public void shuffleAndIndexInstances(Map<String, Applications> remoteRegionsRegistry, EurekaClientConfig clientConfig,
                                         InstanceRegionChecker instanceRegionChecker) {
        _shuffleInstances(clientConfig.shouldFilterOnlyUpInstances(), true, remoteRegionsRegistry, clientConfig,
                          instanceRegionChecker);
    }

    private void _shuffleInstances(boolean filterUpInstances, boolean indexByRemoteRegions,
                                   @Nullable Map<String, Applications> remoteRegionsRegistry,
                                   @Nullable EurekaClientConfig clientConfig,
                                   @Nullable InstanceRegionChecker instanceRegionChecker) {
        this.virtualHostNameAppMap.clear();
        this.secureVirtualHostNameAppMap.clear();
        for (Application application : appNameApplicationMap.values()) {
            if (indexByRemoteRegions) {
                application.shuffleAndStoreInstances(remoteRegionsRegistry, clientConfig, instanceRegionChecker);
            } else {
                application.shuffleAndStoreInstances(filterUpInstances);
            }
            this.addInstancesToVIPMaps(application);
        }
        shuffleAndFilterInstances(this.virtualHostNameAppMap,
                this.shuffleVirtualHostNameMap, virtualHostNameIndexMap,
                filterUpInstances);
        shuffleAndFilterInstances(this.secureVirtualHostNameAppMap,
                this.shuffledSecureVirtualHostNameMap,
                secureVirtualHostNameIndexMap, filterUpInstances);
    }

    /**
     * Gets the next round-robin index for the given virtual host name. This
     * index is reset after every registry fetch cycle.
     * 
     * @param virtualHostname
     *            the virtual host name.
     * @param secure
     *            indicates whether it is a secure request or a non-secure
     *            request.
     * @return AtomicLong value representing the next round-robin index.
     */
    public AtomicLong getNextIndex(String virtualHostname, boolean secure) {
        if (secure) {
            return this.secureVirtualHostNameIndexMap.get(virtualHostname);
        } else {
            return this.virtualHostNameIndexMap.get(virtualHostname);
        }
    }

    /**
     * Shuffle the instances and fiter for only {@link InstanceStatus#UP} if
     * required.
     * 
     */
    private void shuffleAndFilterInstances(
            Map<String, AbstractQueue<InstanceInfo>> srcMap,
            Map<String, AtomicReference<List<InstanceInfo>>> destMap,
            Map<String, AtomicLong> vipIndexMap, boolean filterUpInstances) {
        for (Map.Entry<String, AbstractQueue<InstanceInfo>> entries : srcMap
                .entrySet()) {
            AbstractQueue<InstanceInfo> instanceInfoQueue = entries.getValue();
            List<InstanceInfo> l = new ArrayList<InstanceInfo>(
                    instanceInfoQueue);
            if (filterUpInstances) {
                Iterator<InstanceInfo> it = l.iterator();

                while (it.hasNext()) {
                    InstanceInfo instanceInfo = it.next();
                    if (!InstanceStatus.UP.equals(instanceInfo.getStatus())) {
                        it.remove();
                    }
                }
            }
            Collections.shuffle(l);
            AtomicReference<List<InstanceInfo>> instanceInfoList = destMap
            .get(entries.getKey());
            if (instanceInfoList == null) {
                instanceInfoList = new AtomicReference<List<InstanceInfo>>(l);
                destMap.put(entries.getKey(), instanceInfoList);
            }
            instanceInfoList.set(l);
            vipIndexMap.put(entries.getKey(), new AtomicLong(0));
        }
    }

    /**
     * Add the instance to the given map based if the vip adddress matches with
     * that of the instance. Note that an instance can be mapped to multiple vip
     * adddresses.
     * 
     */
    private void addInstanceToMap(InstanceInfo info, String vipAddresses,
            Map<String, AbstractQueue<InstanceInfo>> vipMap) {
        if (vipAddresses != null) {
            String[] vipAddressArray = vipAddresses.split(",");
            for (String vipAddress : vipAddressArray) {
                String vipName = vipAddress.toUpperCase();
                AbstractQueue<InstanceInfo> instanceInfoList = vipMap
                .get(vipName);
                if (instanceInfoList == null) {
                    instanceInfoList = new ConcurrentLinkedQueue<InstanceInfo>();
                    vipMap.put(vipName, instanceInfoList);
                }
                instanceInfoList.add(info);
            }
        }
    }
    
    /**
     * Adds the instances to the internal vip address map.
     * @param app - the applications for which the instances need to be added.
     */
    private void addInstancesToVIPMaps(Application app) {
        // Check and add the instances to the their respective virtual host name
        // mappings
        for (InstanceInfo info : app.getInstances()) {
            String vipAddresses = info.getVIPAddress();
            String secureVipAddresses = info.getSecureVipAddress();
            if ((vipAddresses == null) && (secureVipAddresses == null)) {
                continue;
            }
            addInstanceToMap(info, vipAddresses, virtualHostNameAppMap);
            addInstanceToMap(info, secureVipAddresses,
                    secureVirtualHostNameAppMap);
        }
    }

}