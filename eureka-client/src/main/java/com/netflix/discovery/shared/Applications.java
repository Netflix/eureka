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

import javax.annotation.Nullable;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.netflix.discovery.util.MapUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.InstanceRegionChecker;
import com.netflix.discovery.provider.Serializer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

/**
 * The class that wraps all the registry information returned by eureka server.
 *
 * <p>
 * Note that the registry information is fetched from eureka server as specified
 * in {@link EurekaClientConfig#getRegistryFetchIntervalSeconds()}. Once the
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
@JsonRootName("applications")
public class Applications {
    private static class VipIndexSupport {
        // Progressive list: emptyList (0) -> singletonList (1) -> ArrayList (2+)
        // This avoids CLQ and Node allocations. 56% of VIPs have exactly 1 instance.
        private List<InstanceInfo> instances = Collections.emptyList();
        final AtomicLong roundRobinIndex = new AtomicLong(0);
        private volatile List<InstanceInfo> vipList = Collections.emptyList();

        void addInstance(InstanceInfo info) {
            int size = instances.size();
            if (size == 0) {
                // 0 -> 1: use singletonList (56% of VIPs stop here)
                instances = Collections.singletonList(info);
            } else if (size == 1) {
                // 1 -> 2: transition singletonList to ArrayList.
                // Capacity 12 chosen based on prod data analysis: covers 81% of multi-instance
                // VIPs without resize (spikes at 6, 9, 12 instances from 3-AZ deployments).
                // ArrayList grows 1.5x (12->18->27), aligning well with common sizes.
                // Capacity 12 minimizes total allocation vs smaller capacities.
                InstanceInfo first = instances.get(0);
                ArrayList<InstanceInfo> list = new ArrayList<>(12);
                list.add(first);
                list.add(info);
                instances = list;
            } else {
                // 2+ -> n: append to ArrayList
                ((ArrayList<InstanceInfo>) instances).add(info);
            }
        }

        int instanceCount() {
            return instances.size();
        }

        List<InstanceInfo> getInstances() {
            return instances;
        }

        public AtomicLong getRoundRobinIndex() {
            return roundRobinIndex;
        }

        List<InstanceInfo> getVipList() {
            return vipList;
        }

        void setVipList(List<InstanceInfo> vipList) {
            this.vipList = vipList;
        }
    }

    private static final String STATUS_DELIMITER = "_";

    private String appsHashCode;
    private Long versionDelta;
    @XStreamImplicit
    private final AbstractQueue<Application> applications;
    private final Map<String, Application> appNameApplicationMap;
    private final Map<String, VipIndexSupport> virtualHostNameAppMap;
    private final Map<String, VipIndexSupport> secureVirtualHostNameAppMap;

    /**
     * Create a new, empty Eureka application list.
     */
    public Applications() {
        this(null, -1L, Collections.emptyList());
    }

    /**
     * Note that appsHashCode and versionDelta key names are formatted in a
     * custom/configurable way.
     */
    @JsonCreator
    public Applications(@JsonProperty("appsHashCode") String appsHashCode,
            @JsonProperty("versionDelta") Long versionDelta,
            @JsonProperty("application") List<Application> registeredApplications) {
        this.applications = new ConcurrentLinkedQueue<Application>();
        this.appNameApplicationMap = new ConcurrentHashMap<String, Application>();
        this.virtualHostNameAppMap = new ConcurrentHashMap<String, VipIndexSupport>();
        this.secureVirtualHostNameAppMap = new ConcurrentHashMap<String, VipIndexSupport>();
        this.appsHashCode = appsHashCode;
        this.versionDelta = versionDelta;

        for (Application app : registeredApplications) {
            this.addApplication(app);
        }
    }

    /**
     * Add the <em>application</em> to the list.
     *
     * @param app
     *            the <em>application</em> to be added.
     */
    public void addApplication(Application app) {
        appNameApplicationMap.put(app.getName().toUpperCase(Locale.ROOT), app);
        addInstancesToVIPMaps(app, this.virtualHostNameAppMap, this.secureVirtualHostNameAppMap);
        applications.add(app);
    }

    /**
     * Gets the list of all registered <em>applications</em> from eureka.
     *
     * @return list containing all applications registered with eureka.
     */
    @JsonProperty("application")
    public List<Application> getRegisteredApplications() {
        return new ArrayList<Application>(this.applications);
    }

    /**
     * Returns whether there are any registered applications.
     * This is more efficient than {@code getRegisteredApplications().isEmpty()}
     * as it avoids creating a defensive copy.
     *
     * @return true if there are no registered applications
     */
    public boolean isRegisteredApplicationsEmpty() {
        return this.applications.isEmpty();
    }

    /**
     * Gets the registered <em>application</em> for the given
     * application name.
     *
     * @param appName
     *            the application name for which the result need to be fetched.
     * @return the registered application for the given application
     *         name.
     */
    public Application getRegisteredApplications(String appName) {
        return appNameApplicationMap.get(appName.toUpperCase(Locale.ROOT));
    }

    /**
     * Gets the list of <em>instances</em> associated to a virtual host name.
     *
     * @param virtualHostName
     *            the virtual hostname for which the instances need to be
     *            returned.
     * @return list of <em>instances</em>.
     */
    public List<InstanceInfo> getInstancesByVirtualHostName(String virtualHostName) {
        return Optional.ofNullable(this.virtualHostNameAppMap.get(virtualHostName.toUpperCase(Locale.ROOT)))
            .map(VipIndexSupport::getVipList)
            .orElseGet(Collections::emptyList);
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
    public List<InstanceInfo> getInstancesBySecureVirtualHostName(String secureVirtualHostName) {
        return Optional.ofNullable(this.secureVirtualHostNameAppMap.get(secureVirtualHostName.toUpperCase(Locale.ROOT)))
                .map(VipIndexSupport::getVipList)
                .orElseGet(Collections::emptyList);
    }

    /**
     * @return a weakly consistent size of the number of instances in all the
     *         applications
     */
    public int size() {
        return applications.stream().mapToInt(Application::size).sum();
    }

    @Deprecated
    public void setVersion(Long version) {
        this.versionDelta = version;
    }

    @Deprecated
    @JsonIgnore // Handled directly due to legacy name formatting
    public Long getVersion() {
        return this.versionDelta;
    }

    /**
     * Used by the eureka server. Not for external use.
     *
     * @param hashCode
     *            the hash code to assign for this app collection
     */
    public void setAppsHashCode(String hashCode) {
        this.appsHashCode = hashCode;
    }

    /**
     * Used by the eureka server. Not for external use.
     * 
     * @return the string indicating the hashcode based on the applications
     *         stored.
     *
     */
    @JsonIgnore // Handled directly due to legacy name formatting
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
    @JsonIgnore
    public String getReconcileHashCode() {
        TreeMap<String, AtomicInteger> instanceCountMap = new TreeMap<String, AtomicInteger>();
        populateInstanceCountMap(instanceCountMap);
        return getReconcileHashCode(instanceCountMap);
    }

    /**
     * Populates the provided instance count map. The instance count map is used
     * as part of the general app list synchronization mechanism.
     * 
     * @param instanceCountMap
     *            the map to populate
     */
    public void populateInstanceCountMap(Map<String, AtomicInteger> instanceCountMap) {
        // accrue here as lightweight as possible
        int[] statusCounts = new int[InstanceStatus.values().length];
        Consumer<InstanceInfo> countByStatus = info -> statusCounts[info.getStatus().ordinal()]++;
        for (Application app : this.applications) {
            app.forEachInstance(countByStatus);
        }

        // now convert it over to the API form in a single pass
        for (InstanceStatus status : InstanceStatus.values()) {
            int count = statusCounts[status.ordinal()];
            if (count > 0) {
                instanceCountMap.computeIfAbsent(status.name(), k -> new AtomicInteger(0))
                    .addAndGet(count);
            }
        }
    }

    /**
     * Gets the reconciliation hashcode. The hashcode is used to determine
     * whether the applications list has changed since the last time it was
     * acquired.
     * 
     * @param instanceCountMap
     *            the instance count map to use for generating the hash
     * @return the hash code for this instance
     */
    public static String getReconcileHashCode(Map<String, AtomicInteger> instanceCountMap) {
        StringBuilder reconcileHashCode = new StringBuilder(75);
        for (Map.Entry<String, AtomicInteger> mapEntry : instanceCountMap.entrySet()) {
            reconcileHashCode.append(mapEntry.getKey()).append(STATUS_DELIMITER).append(mapEntry.getValue().get())
                    .append(STATUS_DELIMITER);
        }
        return reconcileHashCode.toString();
    }

    /**
     * Shuffles the provided instances so that they will not always be returned
     * in the same order.
     * 
     * @param filterUpInstances
     *            whether to return only UP instances
     */
    public void shuffleInstances(boolean filterUpInstances) {
        shuffleInstances(filterUpInstances, false, null, null, null);
    }

    /**
     * Shuffles a whole region so that the instances will not always be returned
     * in the same order.
     * 
     * @param remoteRegionsRegistry
     *            the map of remote region names to their registries
     * @param clientConfig
     *            the {@link EurekaClientConfig}, whose settings will be used to
     *            determine whether to filter to only UP instances
     * @param instanceRegionChecker
     *            the instance region checker
     */
    public void shuffleAndIndexInstances(Map<String, Applications> remoteRegionsRegistry,
            EurekaClientConfig clientConfig, InstanceRegionChecker instanceRegionChecker) {
        shuffleInstances(clientConfig.shouldFilterOnlyUpInstances(), true, remoteRegionsRegistry, clientConfig,
                instanceRegionChecker);
    }

    private void shuffleInstances(boolean filterUpInstances, 
            boolean indexByRemoteRegions,
            @Nullable Map<String, Applications> remoteRegionsRegistry, 
            @Nullable EurekaClientConfig clientConfig,
            @Nullable InstanceRegionChecker instanceRegionChecker) {
        Map<String, VipIndexSupport> secureVirtualHostNameAppMap = MapUtil.newHashMapWithExpectedSize(this.secureVirtualHostNameAppMap.size());
        Map<String, VipIndexSupport> virtualHostNameAppMap = MapUtil.newHashMapWithExpectedSize(this.virtualHostNameAppMap.size());
        for (Application application : appNameApplicationMap.values()) {
            if (indexByRemoteRegions) {
                application.shuffleAndStoreInstances(remoteRegionsRegistry, clientConfig, instanceRegionChecker);
            } else {
                application.shuffleAndStoreInstances(filterUpInstances);
            }
            this.addInstancesToVIPMaps(application, virtualHostNameAppMap, secureVirtualHostNameAppMap);
        }
        shuffleAndFilterInstances(virtualHostNameAppMap, filterUpInstances);
        shuffleAndFilterInstances(secureVirtualHostNameAppMap, filterUpInstances);

        this.virtualHostNameAppMap.putAll(virtualHostNameAppMap);
        this.virtualHostNameAppMap.keySet().retainAll(virtualHostNameAppMap.keySet());
        this.secureVirtualHostNameAppMap.putAll(secureVirtualHostNameAppMap);
        this.secureVirtualHostNameAppMap.keySet().retainAll(secureVirtualHostNameAppMap.keySet());
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
        Map<String, VipIndexSupport> index = (secure) ? secureVirtualHostNameAppMap : virtualHostNameAppMap;
        return Optional.ofNullable(index.get(virtualHostname.toUpperCase(Locale.ROOT)))
                .map(VipIndexSupport::getRoundRobinIndex)
                .orElse(null);
    }

    /**
     * Shuffle the instances and filter for only {@link InstanceStatus#UP} if
     * required.
     *
     */
    private void shuffleAndFilterInstances(Map<String, VipIndexSupport> srcMap, boolean filterUpInstances) {
        Random shuffleRandom = new Random();
        for (Map.Entry<String, VipIndexSupport> entries : srcMap.entrySet()) {
            shuffleAndFilterInstances(entries.getValue(), filterUpInstances, shuffleRandom);
        }
    }

    /**
     * Shuffle and filter instances for a single VIP.
     */
    private void shuffleAndFilterInstances(VipIndexSupport vipIndexSupport, boolean filterUpInstances, Random shuffleRandom) {
        List<InstanceInfo> instances = vipIndexSupport.getInstances();
        int size = instances.size();

        // Empty: nothing to do
        if (size == 0) {
            vipIndexSupport.setVipList(instances);
            return;
        }

        // Single instance: no shuffle needed, check status if filtering
        if (size == 1) {
            InstanceInfo instance = instances.get(0);
            boolean keep = !filterUpInstances || instance.getStatus() == InstanceStatus.UP;
            vipIndexSupport.setVipList(keep ? instances : Collections.emptyList());
            return;
        }

        // Multiple instances (2+): instances is always an ArrayList at this point
        ArrayList<InstanceInfo> list = (ArrayList<InstanceInfo>) instances;

        // Filter in place if needed (no-op when all instances are UP)
        if (filterUpInstances) {
            filterToUpInstancesInPlace(list);
            if (list.isEmpty()) {
                vipIndexSupport.setVipList(Collections.emptyList());
                return;
            }
        }

        // Shuffle in place and reuse
        Collections.shuffle(list, shuffleRandom);
        vipIndexSupport.setVipList(list);
    }

    /**
     * Filter list in place to keep only UP instances. Allocation-free.
     */
    private static void filterToUpInstancesInPlace(ArrayList<InstanceInfo> list) {
        int size = list.size();
        int writeIndex = 0;
        // shift forward all of the UP instances
        for (int i = 0; i < size; i++) {
            InstanceInfo instance = list.get(i);
            if (instance.getStatus() == InstanceStatus.UP) {
                if (writeIndex != i) {
                    list.set(writeIndex, instance);
                }
                writeIndex++;
            }
        }
        // Truncate: remove tail elements. Allows old objects to be GCd.
        // Array is not shrunk back, but, in the majority case this is not useful.
        // More important that we clear the entries so the InstanceInfo elements
        // can be released.
        if (writeIndex < size) {
            list.subList(writeIndex, size).clear();
        }
    }

    /**
     * Add the instance to the given map based if the vip address matches with
     * that of the instance. Note that an instance can be mapped to multiple vip
     * addresses.
     */
    private void addInstanceToMap(InstanceInfo info, String vipAddresses, Map<String, VipIndexSupport> vipMap) {
        // This code path is quite hot on allocations. We apply common-case optimizations to minimize allocations.
        // Gathered statistics from a real cluster:
        // | Metric                | Test   | Prod    |
        // |-----------------------|--------|---------|
        // | Total entries         | N      | 2x N    |
        // | Single VIP (no comma) | 91.1%  | 91.7%   |
        // | 2 VIPs                | 6.9%   | 5.9%    |
        // | 3+ VIPs               | 0.4%   | 0.7%    |
        // | Empty                 | 1.6%   | 1.7%    |
        // | Max VIPs per entry    | 7      | 13      |
        // | Avg string length     | 29.5   | 25.8    |
        // | Max string length     | 204    | 468     |

        // Note: empty vipAddresses is intentionally allowed for backwards compatibility.
        // Legacy behavior: "" creates a mapping with empty string key.
        if (vipAddresses == null) {
            return;
        }

        String upper = vipAddresses.toUpperCase(Locale.ROOT);

        // Fast path (91.7% of cases) single VIP: no split() -> byte[], no substring() -> String
        int commaIndex = upper.indexOf(',');
        if (commaIndex == -1) {
            vipMap.computeIfAbsent(upper, k -> new VipIndexSupport()).addInstance(info);
            return;
        }

        // Multiple VIPs: uppercase once, then parse without split() byte[] allocation
        int start = 0;
        do {
            String vipAddress = upper.substring(start, commaIndex);
            vipMap.computeIfAbsent(vipAddress, k -> new VipIndexSupport()).addInstance(info);
            start = commaIndex + 1;
        } while ((commaIndex = upper.indexOf(',', start)) != -1);

        // Last segment
        String vipAddress = upper.substring(start);
        vipMap.computeIfAbsent(vipAddress, k -> new VipIndexSupport()).addInstance(info);
    }

    /**
     * Adds the instances to the internal vip address map.
     * 
     * @param app
     *            - the applications for which the instances need to be added.
     */
    private void addInstancesToVIPMaps(Application app, Map<String, VipIndexSupport> virtualHostNameAppMap,
            Map<String, VipIndexSupport> secureVirtualHostNameAppMap) {
        // Check and add the instances to the their respective virtual host name
        // mappings
        for (InstanceInfo info : app.getInstances()) {
            String vipAddresses = info.getVIPAddress();
            if (vipAddresses != null) {
                addInstanceToMap(info, vipAddresses, virtualHostNameAppMap);
            }

            String secureVipAddresses = info.getSecureVipAddress();
            if (secureVipAddresses != null) {
                addInstanceToMap(info, secureVipAddresses, secureVirtualHostNameAppMap);
            }
        }
    }

    /**
     * Remove the <em>application</em> from the list.
     *
     * @param app the <em>application</em>
     */
    public void removeApplication(Application app) {
        this.appNameApplicationMap.remove(app.getName().toUpperCase(Locale.ROOT));
        this.applications.remove(app);
    }
}
