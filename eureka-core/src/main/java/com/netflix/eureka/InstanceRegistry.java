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

package com.netflix.eureka;

import static com.netflix.eureka.util.EurekaMonitors.CANCEL;
import static com.netflix.eureka.util.EurekaMonitors.CANCEL_NOT_FOUND;
import static com.netflix.eureka.util.EurekaMonitors.EXPIRED;
import static com.netflix.eureka.util.EurekaMonitors.GET_ALL_CACHE_MISS;
import static com.netflix.eureka.util.EurekaMonitors.GET_ALL_CACHE_MISS_DELTA;
import static com.netflix.eureka.util.EurekaMonitors.GET_ALL_WITH_REMOTE_REGIONS_CACHE_MISS;
import static com.netflix.eureka.util.EurekaMonitors.GET_ALL_WITH_REMOTE_REGIONS_CACHE_MISS_DELTA;
import static com.netflix.eureka.util.EurekaMonitors.REGISTER;
import static com.netflix.eureka.util.EurekaMonitors.RENEW;
import static com.netflix.eureka.util.EurekaMonitors.RENEW_NOT_FOUND;
import static com.netflix.eureka.util.EurekaMonitors.STATUS_UPDATE;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import com.netflix.discovery.shared.Pair;
import com.netflix.eureka.lease.Lease;
import com.netflix.eureka.lease.LeaseManager;
import com.netflix.eureka.resources.ResponseCache;
import com.netflix.eureka.util.AwsAsgUtil;
import com.netflix.eureka.util.MeasuredRate;
import com.netflix.servo.annotations.DataSourceType;

import javax.annotation.Nullable;

/**
 * Handles all registry requests from eureka clients.
 *
 * <p>
 * Primary operations that are performed are the
 * <em>Registers,Renewals,Cancels,Expirations and Status Changes</em>. The
 * registry also stores only the delta operations
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
public abstract class InstanceRegistry implements LeaseManager<InstanceInfo>,
        LookupService<String> {

    private static final Logger logger = LoggerFactory
            .getLogger(InstanceRegistry.class);
    private static final EurekaServerConfig eurekaConfig = EurekaServerConfigurationManager
            .getInstance().getConfiguration();
    public static final String[] EMPTY_STR_ARRAY = new String[0];
    private final ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>> _registry = new ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>>();
    private Timer evictionTimer = new Timer("Eureka-EvictionTimer", true);
    private volatile MeasuredRate renewsLastMin;
    protected ConcurrentMap<String, InstanceStatus> overriddenInstanceStatusMap = CacheBuilder
            .newBuilder().initialCapacity(500)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .<String, InstanceStatus> build().asMap();

    // CircularQueues here for debugging/statistics purposes only
    private CircularQueue<Pair<Long, String>> recentRegisteredQueue;
    private CircularQueue<Pair<Long, String>> recentCanceledQueue;
    private Timer deltaRetentionTimer = new Timer("Eureka-DeltaRetentionTimer",
            true);
    private ConcurrentLinkedQueue<RecentlyChangedItem> recentlyChangedQueue = new ConcurrentLinkedQueue<RecentlyChangedItem>();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock read = readWriteLock.readLock();
    private final Lock write = readWriteLock.writeLock();
    protected Map<String, RemoteRegionRegistry> regionNameVSRemoteRegistry = new HashMap<String, RemoteRegionRegistry>();
    protected String[] allKnownRemoteRegions = EMPTY_STR_ARRAY;
    protected Object lock = new Object();
    protected volatile int numberOfRenewsPerMinThreshold;
    protected volatile int expectedNumberOfRenewsPerMin;
    protected static final EurekaServerConfig EUREKA_SERVER_CONFIG = EurekaServerConfigurationManager
    .getInstance().getConfiguration();

    protected InstanceRegistry() {

        recentCanceledQueue = new CircularQueue<Pair<Long, String>>(1000);
        recentRegisteredQueue = new CircularQueue<Pair<Long, String>>(1000);
        deltaRetentionTimer.schedule(getDeltaRetentionTask(),
                eurekaConfig.getDeltaRetentionTimerIntervalInMs(),
                eurekaConfig.getDeltaRetentionTimerIntervalInMs());
    }

    /*
     * (non-Javadoc)
     *
     * @see com.netflix.eureka.lease.LeaseManager#register(java.lang.Object,
     * int, long, boolean)
     */
    public void register(InstanceInfo r, int leaseDuration,
                         boolean isReplication) {
        try {
            read.lock();
            Map<String, Lease<InstanceInfo>> gMap = _registry.get(r
                    .getAppName());
            REGISTER.increment(isReplication);
            if (gMap == null) {
                final ConcurrentHashMap<String, Lease<InstanceInfo>> gNewMap = new ConcurrentHashMap<String, Lease<InstanceInfo>>();
                gMap = _registry.putIfAbsent(r.getAppName(), gNewMap);
                if (gMap == null)
                    gMap = gNewMap;
            }
            Lease<InstanceInfo> existingLease = gMap.get(r.getId());
            // Retain the last dirty timestamp without overwriting it, if there
            // is already a lease
            if (existingLease != null && (existingLease.getHolder() != null)) {
                Long existingLastDirtyTimestamp = existingLease.getHolder()
                                                               .getLastDirtyTimestamp();
                Long registrationLastDirtyTimestamp = r.getLastDirtyTimestamp();
                if (existingLastDirtyTimestamp > registrationLastDirtyTimestamp) {
                    logger.warn(
                            "There is an existing lease and the existing lease's dirty timestamp {} is greater than the one that is being registered {}",
                            existingLastDirtyTimestamp,
                            registrationLastDirtyTimestamp);
                    r.setLastDirtyTimestamp(existingLastDirtyTimestamp);
                }
            }
            else {
                // The lease does not exist and hence it is a new registration
                synchronized (lock) {
                    if (this.expectedNumberOfRenewsPerMin > 0) {
                        // Since the client wants to cancel it, reduce the threshold
                        // (1
                        // for 30 seconds, 2 for a minute)
                        this.expectedNumberOfRenewsPerMin = this.expectedNumberOfRenewsPerMin + 2;
                        this.numberOfRenewsPerMinThreshold = (int) (this.expectedNumberOfRenewsPerMin * EUREKA_SERVER_CONFIG
                                .getRenewalPercentThreshold());
                    }
                }
            }
            Lease<InstanceInfo> lease = new Lease<InstanceInfo>(r,
                    leaseDuration);
            gMap.put(r.getId(), lease);
            synchronized (recentRegisteredQueue) {
                recentRegisteredQueue.add(new Pair<Long, String>(Long
                        .valueOf(System.currentTimeMillis()), r.getAppName()
                                                              + "(" + r.getId() + ")"));
            }
            // This is where the initial state transfer of overridden status
            // happens
            if (!InstanceStatus.UNKNOWN.equals(r.getOverriddenStatus())) {
                logger.debug(
                        "Found overridden status {} for instance {}. Checking to see if needs to be add to the overrides",
                        r.getOverriddenStatus(), r.getId());
                if (!overriddenInstanceStatusMap.containsKey(r.getId())) {
                    logger.info(
                            "Not found overridden id {} and hence adding it",
                            r.getId());
                    overriddenInstanceStatusMap.put(r.getId(),
                            r.getOverriddenStatus());
                }
            }
            InstanceStatus overriddenStatusFromMap = overriddenInstanceStatusMap.get(r.getId());
            if (overriddenStatusFromMap != null) {
                logger.info(
                        "Storing overridden status {} from map", overriddenStatusFromMap);
                r.setOverriddenStatus(overriddenStatusFromMap);
            }

            // Set the status based on the overridden status rules
            InstanceStatus overriddenInstanceStatus = getOverriddenInstanceStatus(
                    r, existingLease, isReplication);
            r.setStatusWithoutDirty(overriddenInstanceStatus);

            // If the lease is registered with UP status, set lease service up timestamp
            if (InstanceStatus.UP.equals(r.getStatus())) {
                lease.serviceUp();
            }
            if (r != null) {
                r.setActionType(ActionType.ADDED);
                recentlyChangedQueue.add(new RecentlyChangedItem(lease));
                r.setLastUpdatedTimestamp();
            }
            invalidateCache(r.getAppName(), r.getVIPAddress(), r.getSecureVipAddress());
            logger.info("Registered instance id {} with status {}", r.getId(),
                    r.getStatus().toString());
            logger.debug("DS: Registry: registered " + r.getAppName() + " - "
                         + r.getId());
        } finally {
            read.unlock();
        }
    }

    /**
     * Cancels the registration of an instance.
     *
     * <p>
     * This is normally invoked by a client when it shuts down informing the
     * server to remove the instance from traffic.
     * </p>
     *
     * @param appName
     *            the application name of the application.
     * @param id
     *            the unique identifier of the instance.
     * @param isReplication
     *            true if this is a replication event from other nodes, false
     *            otherwise.
     * @return true if the instance was removed from the
     *         {@link InstanceRegistry} successfully, false otherwise.
     */
    public boolean cancel(String appName, String id, boolean isReplication) {
        try {
            read.lock();
            CANCEL.increment(isReplication);
            Map<String, Lease<InstanceInfo>> gMap = _registry.get(appName);
            Lease<InstanceInfo> leaseToCancel = null;
            if (gMap != null) {
                leaseToCancel = gMap.remove(id);
            }
            synchronized (recentCanceledQueue) {
                recentCanceledQueue.add(new Pair<Long, String>(Long
                        .valueOf(System.currentTimeMillis()), appName + "("
                                                              + id + ")"));
            }
            InstanceStatus instanceStatus = overriddenInstanceStatusMap
                    .remove(id);
            if (instanceStatus != null) {
                logger.debug(
                        "Removed instance id {} from the overridden map which has value {}",
                        id, instanceStatus.name());
            }
            if (leaseToCancel == null) {
                CANCEL_NOT_FOUND.increment(isReplication);
                logger.warn("DS: Registry: cancel failed because Lease is not registered for: "
                            + appName + ":" + id);
                return false;
            } else {
                leaseToCancel.cancel();
                InstanceInfo instanceInfo = leaseToCancel.getHolder();
                String vip = null;
                String svip = null;
                if (instanceInfo != null) {
                    instanceInfo.setActionType(ActionType.DELETED);
                    recentlyChangedQueue.add(new RecentlyChangedItem(
                            leaseToCancel));
                    instanceInfo.setLastUpdatedTimestamp();
                    vip = instanceInfo.getVIPAddress();
                    svip = instanceInfo.getSecureVipAddress();
                }
                invalidateCache(appName, vip, svip);
                logger.debug("DS: Registry: canceled lease: " + appName + " - "
                             + id);
                return true;
            }
        } finally {
            read.unlock();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.netflix.eureka.lease.LeaseManager#renew(java.lang.String,
     * java.lang.String, long, boolean)
     */
    public boolean renew(String appName, String id, boolean isReplication) {
        RENEW.increment(isReplication);
        Map<String, Lease<InstanceInfo>> gMap = _registry.get(appName);
        Lease<InstanceInfo> leaseToRenew = null;
        if (gMap != null) {
            leaseToRenew = gMap.get(id);
        }
        if (leaseToRenew == null) {
            RENEW_NOT_FOUND.increment(isReplication);
            logger.warn("DS: Registry: lease doesn't exist, registering resource: "
                        + appName + " - " + id);
            return false;
        } else {
            InstanceInfo instanceInfo = leaseToRenew.getHolder();
            if (instanceInfo != null) {
                // touchASGCache(instanceInfo.getASGName());
                InstanceStatus overriddenInstanceStatus = this
                        .getOverriddenInstanceStatus(instanceInfo,
                                leaseToRenew, isReplication);
                // InstanceStatus overriddenInstanceStatus =
                // instanceInfo.getStatus();
                if (!instanceInfo.getStatus().equals(overriddenInstanceStatus)) {
                    Object[] args = { instanceInfo.getStatus().name(),
                                      instanceInfo.getOverriddenStatus().name(),
                                      instanceInfo.getId() };
                    logger.info(
                            "The instance status {} is different from overridden instance status {} for instance {}. Hence setting the status to overridden status",
                            args);
                    instanceInfo.setStatus(overriddenInstanceStatus);
                }
            }
            renewsLastMin.increment();
            leaseToRenew.renew();
            return true;
        }
    }

    /**
     * Stores overridden status if it is not already there. This happens during
     * a reconciliation process during renewal requests.
     *
     * @param id
     *            the unique identifier of the instance.
     * @param overriddenStatus
     *            Overridden status if any.
     */
    public void storeOverriddenStatusIfRequired(String id,
                                                InstanceStatus overriddenStatus) {
        InstanceStatus instanceStatus = overriddenInstanceStatusMap.get(id);
        if ((instanceStatus == null)
            || (!overriddenStatus.equals(instanceStatus))) {
            // We might not have the overridden status if the server got
            // restarted -this will help us maintain the overridden state
            // from the replica
            logger.info(
                    "Adding overridden status for instance id {} and the value is {}",
                    id, overriddenStatus.name());
            overriddenInstanceStatusMap.put(id, overriddenStatus);
            List<InstanceInfo> instanceInfo = this.getInstancesById(id, false);
            if ((instanceInfo != null) && (!instanceInfo.isEmpty())) {
                instanceInfo.iterator().next().setOverriddenStatus(overriddenStatus);
                logger.info(
                        "Setting the overridden status for instance id {} and the value is {} ",
                        id, overriddenStatus.name());

            }
        }
    }

    /**
     * Updates the status of an instance. Normally happens to put an instance
     * between {@link InstanceStatus#OUT_OF_SERVICE} and
     * {@link InstanceStatus#UP} to put the instance in and out of traffic.
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @param newStatus
     *            the new {@link InstanceStatus}.
     * @param lastDirtyTimestamp
     *            last timestamp when this instance information was updated.
     * @param isReplication
     *            true if this is a replication event from other nodes, false
     *            otherwise.
     * @return true if the status was successfully updated, false otherwise.
     */
    public boolean statusUpdate(String appName, String id,
                                InstanceStatus newStatus, String lastDirtyTimestamp,
                                boolean isReplication) {
        try {
            read.lock();
            STATUS_UPDATE.increment(isReplication);
            Map<String, Lease<InstanceInfo>> gMap = _registry.get(appName);
            Lease<InstanceInfo> lease = null;
            if (gMap != null) {
                lease = gMap.get(id);
            }
            if (lease == null) {
                return false;
            } else {
                lease.renew();
                InstanceInfo info = lease.getHolder();
                if ((info != null) && !(info.getStatus().equals(newStatus))) {
                    // Mark service as UP if needed
                    if (InstanceStatus.UP.equals(newStatus)) {
                        lease.serviceUp();
                    }
                    // This is NAC overriden status
                    overriddenInstanceStatusMap.put(id, newStatus);
                    // Set it for transfer of overridden status to replica on
                    // replica start up
                    info.setOverriddenStatus(newStatus);
                    long replicaDirtyTimestamp = 0;
                    if (lastDirtyTimestamp != null) {
                        replicaDirtyTimestamp = Long
                                .valueOf(lastDirtyTimestamp);
                    }
                    // If the replication's dirty timestamp is more than the
                    // existing one, just update
                    // it to the replica's.
                    if (replicaDirtyTimestamp > info.getLastDirtyTimestamp()) {
                        info.setLastDirtyTimestamp(replicaDirtyTimestamp);
                        info.setStatusWithoutDirty(newStatus);
                    } else {
                        info.setStatus(newStatus);
                    }
                    if (info != null) {
                        info.setActionType(ActionType.MODIFIED);
                        recentlyChangedQueue
                                .add(new RecentlyChangedItem(lease));
                        info.setLastUpdatedTimestamp();
                    }
                    invalidateCache(appName, info.getVIPAddress(), info.getSecureVipAddress());
                }
                return true;
            }
        } finally {
            read.unlock();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.netflix.eureka.lease.LeaseManager#evict()
     */
    public void evict() {

        if (!isLeaseExpirationEnabled()) {
            logger.debug("DS: lease expiration is currently disabled.");
            return;
        }
        logger.debug("Running the evict task");
        for (Iterator<Entry<String, Map<String, Lease<InstanceInfo>>>> iter = _registry
                .entrySet().iterator(); iter.hasNext();) {
            Entry<String, Map<String, Lease<InstanceInfo>>> groupEntry = iter
                    .next();

            Map<String, Lease<InstanceInfo>> leaseMap = groupEntry.getValue();
            if (leaseMap != null) {
                for (Iterator<Entry<String, Lease<InstanceInfo>>> subIter = leaseMap
                        .entrySet().iterator(); subIter.hasNext();) {
                    Entry<String, Lease<InstanceInfo>> leaseEntry = subIter
                            .next();
                    Lease<InstanceInfo> lease = leaseEntry.getValue();
                    if (lease.isExpired() && lease.getHolder() != null) {
                        String appName = lease.getHolder().getAppName();
                        String id = lease.getHolder().getId();
                        EXPIRED.increment();
                        logger.warn("DS: Registry: expired lease for "
                                    + appName + " - " + id);
                        cancel(appName, id, false);
                    }
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.netflix.eureka.shared.LookupService#getApplication(java.lang.String )
     */
    public Application getApplication(String appName) {
        boolean disableTransparentFallback = eurekaConfig.disableTransparentFallbackToOtherRegion();
        return this.getApplication(appName, !disableTransparentFallback);
    }

    /**
     * Get application information.
     *
     * @param appName
     *            - The name of the application
     * @param includeRemoteRegion
     *            - true, if we need to include applications from remote regions
     *            as indicated by the region {@link URL} by this property
     *            {@link EurekaServerConfig#getRemoteRegionUrls()}, false
     *            otherwise
     * @return
     */
    public Application getApplication(String appName, boolean includeRemoteRegion) {
        Application app = null;

        Map<String, Lease<InstanceInfo>> leaseMap = _registry.get(appName);

        if (leaseMap != null && leaseMap.size() > 0) {
            for (Iterator<Entry<String, Lease<InstanceInfo>>> iter = leaseMap
                    .entrySet().iterator(); iter.hasNext();) {
                Entry<String, Lease<InstanceInfo>> entry = iter.next();

                if (app == null) {
                    app = new Application(appName);
                }
                app.addInstance(decorateInstanceInfo(entry.getValue()));
            }
        } else if (includeRemoteRegion) {
            for (RemoteRegionRegistry remoteRegistry : this.regionNameVSRemoteRegistry.values()) {
                Application application = remoteRegistry.getApplication(appName);
                if (application != null) {
                    return application;
                }
            }
        }
        return app;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.netflix.discovery.shared.LookupService#getApplications()
     */
    public Applications getApplications() {
        boolean disableTransparentFallback = eurekaConfig.disableTransparentFallbackToOtherRegion();
        if (disableTransparentFallback) {
            return getApplicationsFromLocalRegionOnly();
        } else {
            return this.getApplications(true); // Behavior of falling back to remote region can be disabled.
        }
    }

    /**
     * Returns applications including instances from all remote regions. <br/>
     * Same as calling {@link #getApplicationsFromMultipleRegions(String[])} with a <code>null</code> argument.
     */
    public Applications getApplicationsFromAllRemoteRegions() {
        return getApplicationsFromMultipleRegions(allKnownRemoteRegions);
    }

    /**
     * Returns applications including instances from local region only. <br/>
     * Same as calling {@link #getApplicationsFromMultipleRegions(String[])} with an empty array.
     */
    public Applications getApplicationsFromLocalRegionOnly() {
        return getApplicationsFromMultipleRegions(EMPTY_STR_ARRAY);
    }

    /**
     * This method will return applications with instances from all passed remote regions as well as the current region.
     * Thus, this gives a union view of instances from multiple regions. <br/>
     * The application instances for which this union will be done can be restricted to the names returned by
     * {@link EurekaServerConfig#getRemoteRegionAppWhitelist(String)} for every region. In case, there is no whitelist
     * defined for a region, this method will also look for a global whitelist by passing <code>null</code> to the
     * method {@link EurekaServerConfig#getRemoteRegionAppWhitelist(String)} <br/>
     * If you are not selectively requesting for a remote region, use {@link #getApplicationsFromAllRemoteRegions()}
     * or {@link #getApplicationsFromLocalRegionOnly()}
     *
     * @param remoteRegions The remote regions for which the instances are to be queried. The instances may be limited
     *                      by a whitelist as explained above. If <code>null</code> or empty no remote regions are included.
     *
     * @return The applications with instances from the passed remote regions as well as local region. The instances
     * from remote regions can be only for certain whitelisted apps as explained above.
     */
    public Applications getApplicationsFromMultipleRegions(String[] remoteRegions) {

        boolean includeRemoteRegion = null != remoteRegions && remoteRegions.length != 0;

        logger.info("Fetching applications registry with remote regions: {}, Regions argument {}", includeRemoteRegion,
                    Arrays.toString(remoteRegions));

        if (includeRemoteRegion) {
            GET_ALL_WITH_REMOTE_REGIONS_CACHE_MISS.increment();
        } else {
            GET_ALL_CACHE_MISS.increment();
        }
        Applications apps = new Applications();
        apps.setVersion(1L);
        for (Entry<String, Map<String, Lease<InstanceInfo>>> entry : _registry.entrySet()) {
            Application app = null;

            if (entry.getValue() != null) {
                for (Entry<String, Lease<InstanceInfo>> stringLeaseEntry : entry.getValue().entrySet()) {
                    Lease<InstanceInfo> lease = stringLeaseEntry.getValue();
                    if (app == null) {
                        app = new Application(lease.getHolder().getAppName());
                    }
                    app.addInstance(decorateInstanceInfo(lease));
                }
            }
            if (app != null) {
                apps.addApplication(app);
            }
        }
        if (includeRemoteRegion) {
            for (String remoteRegion : remoteRegions) {
                RemoteRegionRegistry remoteRegistry = regionNameVSRemoteRegistry.get(remoteRegion);
                if (null != remoteRegistry) {
                    Applications remoteApps = remoteRegistry.getApplications();
                    for (Application application : remoteApps.getRegisteredApplications()) {
                        if (shouldFetchFromRemoteRegistry(application.getName(), remoteRegion)) {
                            Application appInstanceTillNow = apps.getRegisteredApplications(application.getName());
                            if (appInstanceTillNow == null) {
                                appInstanceTillNow = new Application(application.getName());
                                apps.addApplication(appInstanceTillNow);
                            }
                            for (InstanceInfo instanceInfo : application.getInstances()) {
                                appInstanceTillNow.addInstance(instanceInfo);
                            }
                        } else {
                            logger.info("Application {} not fetched from the remote region {} as there exists a whitelist and this app is not in the whitelist.",
                                        application.getName(), remoteRegion);
                        }
                    }
                } else {
                    logger.warn("No remote registry available for the remote region {}", remoteRegion);
                }
            }
        }
        apps.setAppsHashCode(apps.getReconcileHashCode());
        return apps;
    }

    private boolean shouldFetchFromRemoteRegistry(String appName, String remoteRegion) {
        Set<String> whiteList = eurekaConfig.getRemoteRegionAppWhitelist(remoteRegion);
        if (null == whiteList) {
            whiteList = eurekaConfig.getRemoteRegionAppWhitelist(null); // see global whitelist.
        }
        return null == whiteList || whiteList.contains(appName);
    }

    /**
     * Get the registry information about all {@link Applications}.
     *
     * @return all applications.
     * @param includeRemoteRegion
     *            - true, if we need to include applications from remote regions
     *            as indicated by the region {@link URL} by this property
     *            {@link EurekaServerConfig#getRemoteRegionUrls()}, false
     *            otherwise
     * @return applications
     * @deprecated Use {@link #getApplicationsFromMultipleRegions(String[])} instead. This method has a flawed behavior
     * of transparently falling back to a remote region if no instances for an app is available locally. The new
     * behavior is to explictly specify if you need a remote region.
     */
    @Deprecated
    public Applications getApplications(boolean includeRemoteRegion) {
        GET_ALL_CACHE_MISS.increment();
        Applications apps = new Applications();
        apps.setVersion(1L);
        for (Iterator<Entry<String, Map<String, Lease<InstanceInfo>>>> iter = _registry
                .entrySet().iterator(); iter.hasNext();) {
            Entry<String, Map<String, Lease<InstanceInfo>>> entry = iter.next();

            Application app = null;

            if (entry.getValue() != null) {
                for (Iterator<Entry<String, Lease<InstanceInfo>>> subIter = entry
                        .getValue().entrySet().iterator(); subIter.hasNext();) {

                    Lease<InstanceInfo> lease = subIter.next().getValue();

                    if (app == null) {
                        app = new Application(lease.getHolder().getAppName());
                    }

                    app.addInstance(decorateInstanceInfo(lease));
                }
            }
            if (app != null) {
                apps.addApplication(app);
            }
        }
        if (includeRemoteRegion) {
            for (RemoteRegionRegistry remoteRegistry : this.regionNameVSRemoteRegistry.values()) {
                Applications applications = remoteRegistry.getApplications();
                for (Application application : applications
                        .getRegisteredApplications()) {
                    Application appInLocalRegistry = apps
                            .getRegisteredApplications(application.getName());
                    if (appInLocalRegistry == null) {
                        apps.addApplication(application);
                    }
                }
            }
        }
        apps.setAppsHashCode(apps.getReconcileHashCode());
        return apps;
    }

    /**
     * Get the registry information about the delta changes. The deltas are
     * cached for a window specified by
     * {@link EurekaServerConfig#getRetentionTimeInMSInDeltaQueue()}. Subsequent
     * requests for delta information may return the same information and client
     * must make sure this does not adversely affect them.
     *
     * @return all application deltas.
     * @deprecated use {@link #getApplicationDeltasFromMultipleRegions(String[])} instead. This method has a flawed behavior
     * of transparently falling back to a remote region if no instances for an app is available locally. The new
     * behavior is to explictly specify if you need a remote region.
     */
    @Deprecated
    public Applications getApplicationDeltas() {
        GET_ALL_CACHE_MISS_DELTA.increment();
        Applications apps = new Applications();
        apps.setVersion(ResponseCache.getVersionDelta().get());
        Map<String, Application> applicationInstancesMap = new HashMap<String, Application>();
        try {
            write.lock();
            Iterator<RecentlyChangedItem> iter = this.recentlyChangedQueue.iterator();
            logger.debug("The number of elements in the delta queue is :"
                         + this.recentlyChangedQueue.size());
            while (iter.hasNext()) {
                Lease<InstanceInfo> lease = iter.next().getLeaseInfo();
                InstanceInfo instanceInfo = lease.getHolder();
                Object[] args = { instanceInfo.getId(),
                                  instanceInfo.getStatus().name(),
                                  instanceInfo.getActionType().name() };
                logger.debug(
                        "The instance id %s is found with status %s and actiontype %s",
                        args);
                Application app = applicationInstancesMap.get(instanceInfo
                        .getAppName());
                if (app == null) {
                    app = new Application(instanceInfo.getAppName());
                    applicationInstancesMap.put(instanceInfo.getAppName(), app);
                    apps.addApplication(app);
                }
                app.addInstance(decorateInstanceInfo(lease));
            }

            boolean disableTransparentFallback = eurekaConfig.disableTransparentFallbackToOtherRegion();

            if (!disableTransparentFallback) {
                Applications allAppsInLocalRegion = getApplications(false);

                for (RemoteRegionRegistry remoteRegistry : this.regionNameVSRemoteRegistry.values()) {
                    Applications applications = remoteRegistry.getApplicationDeltas();
                    for (Application application : applications.getRegisteredApplications()) {
                        Application appInLocalRegistry = allAppsInLocalRegion.getRegisteredApplications(application.getName());
                        if (appInLocalRegistry == null) {
                            apps.addApplication(application);
                        }
                    }
                }
            }

            Applications allApps = getApplications(!disableTransparentFallback);
            apps.setAppsHashCode(allApps.getReconcileHashCode());
            return apps;
        } finally {
            write.unlock();
        }
    }

    /**
     * Gets the application delta also including instances from the passed remote regions, with the instances from the
     * local region. <br/>
     *
     * The remote regions from where the instances will be chosen can further be restricted if this application does not
     * appear in the whitelist specified for the region as returned by
     * {@link EurekaServerConfig#getRemoteRegionAppWhitelist(String)} for a region. In case, there is no whitelist
     * defined for a region, this method will also look for a global whitelist by passing <code>null</code> to the
     * method {@link EurekaServerConfig#getRemoteRegionAppWhitelist(String)} <br/>
     *
     * @param remoteRegions The remote regions for which the instances are to be queried. The instances may be limited
     *                      by a whitelist as explained above. If <code>null</code> all remote regions are included.
     *                      If empty list then no remote region is included.
     *
     * @return The delta with instances from the passed remote regions as well as local region. The instances
     * from remote regions can be further be restricted as explained above. <code>null</code> if the application does
     * not exist locally or in remote regions.
     */
    public Applications getApplicationDeltasFromMultipleRegions(String[] remoteRegions) {
        if (null == remoteRegions) {
            remoteRegions = allKnownRemoteRegions; // null means all remote regions.
        }

        boolean includeRemoteRegion = remoteRegions.length != 0;

        if (includeRemoteRegion) {
            GET_ALL_WITH_REMOTE_REGIONS_CACHE_MISS_DELTA.increment();
        } else {
            GET_ALL_CACHE_MISS_DELTA.increment();
        }

        Applications apps = new Applications();
        apps.setVersion(ResponseCache.getVersionDeltaWithRegions().get());
        Map<String, Application> applicationInstancesMap = new HashMap<String, Application>();
        try {
            write.lock();
            Iterator<RecentlyChangedItem> iter = this.recentlyChangedQueue.iterator();
            logger.debug("The number of elements in the delta queue is :" + this.recentlyChangedQueue.size());
            while (iter.hasNext()) {
                Lease<InstanceInfo> lease = iter.next().getLeaseInfo();
                InstanceInfo instanceInfo = lease.getHolder();
                Object[] args = { instanceInfo.getId(),
                                  instanceInfo.getStatus().name(),
                                  instanceInfo.getActionType().name() };
                logger.debug(
                        "The instance id %s is found with status %s and actiontype %s",
                        args);
                Application app = applicationInstancesMap.get(instanceInfo
                        .getAppName());
                if (app == null) {
                    app = new Application(instanceInfo.getAppName());
                    applicationInstancesMap.put(instanceInfo.getAppName(), app);
                    apps.addApplication(app);
                }
                app.addInstance(decorateInstanceInfo(lease));
            }

            if (includeRemoteRegion) {
                for (String remoteRegion : remoteRegions) {
                    RemoteRegionRegistry remoteRegistry = regionNameVSRemoteRegistry.get(remoteRegion);
                    if (null != remoteRegistry) {
                        Applications remoteAppsDelta = remoteRegistry.getApplicationDeltas();
                        if (null != remoteAppsDelta) {
                            for (Application application : remoteAppsDelta.getRegisteredApplications()) {
                                if (shouldFetchFromRemoteRegistry(application.getName(), remoteRegion)) {
                                    Application appInstanceTillNow = apps.getRegisteredApplications(application.getName());
                                    if (appInstanceTillNow == null) {
                                        appInstanceTillNow = new Application(application.getName());
                                        apps.addApplication(appInstanceTillNow);
                                    }
                                    for (InstanceInfo instanceInfo : application.getInstances()) {
                                        appInstanceTillNow.addInstance(instanceInfo);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Applications allApps = getApplicationsFromMultipleRegions(remoteRegions);
            apps.setAppsHashCode(allApps.getReconcileHashCode());
            return apps;
        } finally {
            write.unlock();
        }
    }

    /**
     * Gets the {@link InstanceInfo} information.
     *
     * @param appName
     *            the application name for which the information is requested.
     * @param id
     *            the unique identifier of the instance.
     * @return the information about the instance.
     */
    public InstanceInfo getInstanceByAppAndId(String appName, String id) {
        return this.getInstanceByAppAndId(appName, id, true);
    }

    /**
     * Gets the {@link InstanceInfo} information.
     *
     * @param appName
     *            the application name for which the information is requested.
     * @param id
     *            the unique identifier of the instance.
     * @param includeRemoteRegions
     *            - true, if we need to include applications from remote regions
     *            as indicated by the region {@link URL} by this property
     *            {@link EurekaServerConfig#getRemoteRegionUrls()}, false
     *            otherwise
     * @return the information about the instance.
     */
    public InstanceInfo getInstanceByAppAndId(String appName, String id,
                                              boolean includeRemoteRegions) {
        Map<String, Lease<InstanceInfo>> leaseMap = _registry.get(appName);
        Lease<InstanceInfo> lease = null;
        if (leaseMap != null) {
            lease = leaseMap.get(id);
        }
        if (lease != null
            && (!isLeaseExpirationEnabled() || !lease.isExpired())) {
            return decorateInstanceInfo(lease);
        } else if (includeRemoteRegions) {
            for (RemoteRegionRegistry remoteRegistry : this.regionNameVSRemoteRegistry.values()) {
                Application application = remoteRegistry.getApplication(appName);
                InstanceInfo instanceInfo = application.getByInstanceId(id);
                return instanceInfo;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.netflix.eureka.shared.LookupService#getInstancesById(java.lang
     * .String)
     */
    public List<InstanceInfo> getInstancesById(String id) {
        return this.getInstancesById(id, true);
    }

    /**
     * Get the list of instances by its unique id.
     *
     * @param id
     *            - the unique id of the instance
     * @param includeRemoteRegions
     *            - true, if we need to include applications from remote regions
     *            as indicated by the region {@link URL} by this property
     *            {@link EurekaServerConfig#getRemoteRegionUrls()}, false
     *            otherwise
     * @return list of InstanceInfo objects.
     */
    public List<InstanceInfo> getInstancesById(String id,
                                               boolean includeRemoteRegions) {
        List<InstanceInfo> list = new ArrayList<InstanceInfo>();

        for (Iterator<Entry<String, Map<String, Lease<InstanceInfo>>>> iter = _registry
                .entrySet().iterator(); iter.hasNext();) {

            Map<String, Lease<InstanceInfo>> leaseMap = iter.next().getValue();
            if (leaseMap != null) {
                Lease<InstanceInfo> lease = leaseMap.get(id);

                if (lease == null
                    || (isLeaseExpirationEnabled() && lease.isExpired())) {
                    continue;
                }

                if (lease != null && list == Collections.EMPTY_LIST) {
                    list = new ArrayList<InstanceInfo>();
                }

                if (lease != null) {
                    list.add(decorateInstanceInfo(lease));
                }
            }
        }
        if (list.isEmpty() && includeRemoteRegions) {
            for (RemoteRegionRegistry remoteRegistry : this.regionNameVSRemoteRegistry.values()) {
                for (Application application : remoteRegistry.getApplications()
                                                             .getRegisteredApplications()) {
                    InstanceInfo instanceInfo = application.getByInstanceId(id);
                    if (instanceInfo != null) {
                        list.add(instanceInfo);
                        return list;
                    }
                }
            }
        }
        return list;
    }

    public abstract boolean isLeaseExpirationEnabled();

    private InstanceInfo decorateInstanceInfo(Lease<InstanceInfo> lease) {
        InstanceInfo info = lease.getHolder();

        // client app settings
        int renewalInterval = LeaseInfo.DEFAULT_LEASE_RENEWAL_INTERVAL;
        int leaseDuration = LeaseInfo.DEFAULT_LEASE_DURATION;

        // TODO: clean this up
        if (info.getLeaseInfo() != null) {
            renewalInterval = info.getLeaseInfo().getRenewalIntervalInSecs();
            leaseDuration = info.getLeaseInfo().getDurationInSecs();
        }

        info.setLeaseInfo(LeaseInfo.Builder.newBuilder()
                                   .setRegistrationTimestamp(lease.getRegistrationTimestamp())
                                   .setRenewalTimestamp(lease.getLastRenewalTimestamp())
                                   .setServiceUpTimestamp(lease.getServiceUpTimestamp())
                                   .setRenewalIntervalInSecs(renewalInterval)
                                   .setDurationInSecs(leaseDuration)
                                   .setEvictionTimestamp(lease.getEvictionTimestamp()).build());

        info.setIsCoordinatingDiscoveryServer();
        return info;
    }

    @com.netflix.servo.annotations.Monitor(name = "numOfRenewsInLastMin", description = "Number of total heartbeats received in the last minute", type = DataSourceType.GAUGE)
    public long getNumOfRenewsInLastMin() {
        if (renewsLastMin != null) {
            return renewsLastMin.getCount();
        } else {
            return 0;
        }
    }

    public List<Pair<Long, String>> getLastNRegisteredInstances() {
        List<Pair<Long, String>> list = new ArrayList<Pair<Long, String>>();

        synchronized (recentRegisteredQueue) {
            for (Iterator<Pair<Long, String>> iter = recentRegisteredQueue
                    .iterator(); iter.hasNext();) {
                list.add(iter.next());
            }
        }
        Collections.reverse(list);
        return list;
    }

    public List<Pair<Long, String>> getLastNCanceledInstances() {
        List<Pair<Long, String>> list = new ArrayList<Pair<Long, String>>();
        synchronized (recentCanceledQueue) {
            for (Iterator<Pair<Long, String>> iter = recentCanceledQueue
                    .iterator(); iter.hasNext();) {
                list.add(iter.next());
            }
        }
        Collections.reverse(list);
        return list;
    }

    private void invalidateCache(String appName, @Nullable String vipAddress, @Nullable String secureVipAddress) {
        // invalidate cache
        ResponseCache.getInstance().invalidate(appName, vipAddress, secureVipAddress);
    }

    private static final class RecentlyChangedItem {
        private long lastUpdateTime;
        private Lease<InstanceInfo> leaseInfo;

        public RecentlyChangedItem(Lease<InstanceInfo> lease) {
            this.leaseInfo = lease;
            lastUpdateTime = System.currentTimeMillis();
        }

        public long getLastUpdateTime() {
            return this.lastUpdateTime;
        }

        public Lease<InstanceInfo> getLeaseInfo() {
            return this.leaseInfo;
        }
    }

    protected void postInit() {
        renewsLastMin = new MeasuredRate(1000 * 60 * 1);
        evictionTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                try {
                    evict();
                } catch (Throwable e) {
                    logger.error("Could not run the evict task", e);
                }

            }
        }, eurekaConfig.getEvictionIntervalTimerInMs(),
                eurekaConfig.getEvictionIntervalTimerInMs());
    }

    @com.netflix.servo.annotations.Monitor(name = "numOfElementsinInstanceCache", description = "Number of elements in the instance Cache", type = DataSourceType.GAUGE)
    public long getNumberofElementsininstanceCache() {
        return overriddenInstanceStatusMap.size();
    }

    private class CircularQueue<E> extends ConcurrentLinkedQueue<E> {
        int size = 0;

        public CircularQueue(int size) {
            this.size = size;
        }

        @Override
        public boolean add(E e) {
            this.makeSpaceIfnotAvailable();
            return super.add(e);

        }

        private void makeSpaceIfnotAvailable() {
            if (this.size() == size) {
                this.remove();
            }
        }

        public boolean offer(E e) {
            this.makeSpaceIfnotAvailable();
            return super.offer(e);
        }
    }

    private InstanceStatus getOverriddenInstanceStatus(InstanceInfo r,
                                                       Lease<InstanceInfo> existingLease, boolean isReplication) {
        // Instance is DOWN or STARTING - believe that, but when the instance
        // says UP, question that
        // The client instance sends STARTING or DOWN (because of heartbeat
        // failures), then we accept what
        // the client says. The same is the case with replica as well.
        // The OUT_OF_SERVICE from the client or replica needs to be confirmed
        // as well since the service may be
        // currently in SERVICE
        if ((!InstanceStatus.UP.equals(r.getStatus()))
            && (!InstanceStatus.OUT_OF_SERVICE.equals(r.getStatus()))) {
            logger.debug(
                    "Trusting the instance status {} from replica or instance for instance",
                    r.getStatus(), r.getId());
            return r.getStatus();
        }
        // Overrides are the status like OUT_OF_SERVICE and UP set by NAC
        InstanceStatus overridden = overriddenInstanceStatusMap.get(r.getId());
        // If there are instance specific overrides, then they win - otherwise
        // the ASG status
        if (overridden != null) {
            logger.debug(
                    "The instance specific override for instance {} and the value is {}",
                    r.getId(), overridden.name());
            return overridden;
        }
        // If the ASGName is present- check for its status
        boolean isASGDisabled = false;
        if (r.getASGName() != null) {
            isASGDisabled = !AwsAsgUtil.getInstance().isASGEnabled(
                    r.getASGName());
            logger.debug("The ASG name is specified {} and the value is {}",
                    r.getASGName(), isASGDisabled);
            if (isASGDisabled) {
                return InstanceStatus.OUT_OF_SERVICE;
            } else {
                return InstanceStatus.UP;
            }
        }
        // This is for backward compatibility until all applications have ASG
        // names, otherwise while starting up
        // the client status may override status replicated from other servers
        if (!isReplication) {
            InstanceStatus existingStatus = null;
            if (existingLease != null) {
                existingStatus = existingLease.getHolder().getStatus();
            }
            // Allow server to have its way when the status is UP or
            // OUT_OF_SERVICE
            if ((existingStatus != null)
                && (InstanceStatus.OUT_OF_SERVICE.equals(existingStatus) || InstanceStatus.UP
                                                                                          .equals(existingStatus))) {
                logger.debug(
                        "There is already an existing lease with status {}  for instance {}",
                        existingLease.getHolder().getStatus().name(),
                        existingLease.getHolder().getId());
                return existingLease.getHolder().getStatus();
            }
        }
        logger.debug(
                "Returning the default instance status {} for instance {},",
                r.getStatus(), r.getId());
        return r.getStatus();
    }

    private TimerTask getDeltaRetentionTask() {
        return new TimerTask() {

            @Override
            public void run() {
                Iterator<RecentlyChangedItem> it = recentlyChangedQueue
                        .iterator();
                while (it.hasNext()) {
                    if (it.next().getLastUpdateTime() < System
                                                                .currentTimeMillis()
                                                        - eurekaConfig.getRetentionTimeInMSInDeltaQueue()) {
                        it.remove();
                    } else {
                        break;
                    }
                }
            }

        };
    }

    protected void initRemoteRegionRegistry() throws MalformedURLException {
        Map<String, String> remoteRegionUrlsWithName = eurekaConfig.getRemoteRegionUrlsWithName();
        if (remoteRegionUrlsWithName != null) {
            allKnownRemoteRegions = new String[remoteRegionUrlsWithName.size()];
            int remoteRegionArrayIndex = 0;
            for (Entry<String, String> remoteRegionUrlWithName : remoteRegionUrlsWithName.entrySet()) {
                RemoteRegionRegistry remoteRegionRegistry = new RemoteRegionRegistry(new URL(remoteRegionUrlWithName.getValue()));
                regionNameVSRemoteRegistry.put(remoteRegionUrlWithName.getKey(), remoteRegionRegistry);
                allKnownRemoteRegions[remoteRegionArrayIndex++] = remoteRegionUrlWithName.getKey();
            }
        }
        logger.info("Finished initializing remote region registries. All known remote regions: {}",
                    Arrays.toString(allKnownRemoteRegions));
    }

}
