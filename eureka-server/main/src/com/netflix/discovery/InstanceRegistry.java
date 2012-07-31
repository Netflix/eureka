/*
 * ResourceRegistry.java
 *
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/InstanceRegistry.java#4 $
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

import static com.netflix.discovery.util.DSCounter.CANCEL;
import static com.netflix.discovery.util.DSCounter.CANCEL_NOT_FOUND;
import static com.netflix.discovery.util.DSCounter.EXPIRED;
import static com.netflix.discovery.util.DSCounter.GET_ALL_CACHE_MISS;
import static com.netflix.discovery.util.DSCounter.GET_ALL_CACHE_MISS_DELTA;
import static com.netflix.discovery.util.DSCounter.REGISTER;
import static com.netflix.discovery.util.DSCounter.RENEW;
import static com.netflix.discovery.util.DSCounter.RENEW_NOT_FOUND;
import static com.netflix.discovery.util.DSCounter.STATUS_UPDATE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.cache.CacheBuilder;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.lease.Lease;
import com.netflix.discovery.lease.LeaseManager;
import com.netflix.discovery.resources.ResponseCache;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import com.netflix.discovery.shared.Pair;
import com.netflix.discovery.util.AwsAsgUtil;
import com.netflix.discovery.util.MeasuredRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;


/**
 * Application instance registry.
 *
 * @author gkim
 */
public abstract class InstanceRegistry implements LeaseManager<InstanceInfo>, LookupService<String> {

    private static final int RCQ_CLEANUP_TIMER = 30000;
    private static final int TIME_MS_IN_RECENTLY_CHANGED_QUEUE = 3*60*1000;
    private static final Logger s_logger = LoggerFactory.getLogger(InstanceRegistry.class); 
    private static final int EVICTION_DELAY_IN_MS = 60000;
    protected static final String TRACER_GET_APPS_DIFFERENTIAL = "Discovery:storeAppsDifferential";

    private final ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>> _registry =
        new ConcurrentHashMap<String, Map<String,Lease<InstanceInfo>>>();

    private Timer _scheduledEvictionTimer = new Timer( "Eviction_Thread", true);

    private volatile MeasuredRate _renewsInLastMin;
    
    protected ConcurrentMap<String, InstanceStatus> overriddenInstanceStatusMap =  CacheBuilder.newBuilder()
    .initialCapacity(500)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .<String, InstanceStatus>build().asMap();

    //CircularQueues here for debugging/statistics purposes only
    private CircularQueue<Pair<Long, String>> _recentRegisteredQueue;
    private CircularQueue<Pair<Long, String>> _recentCanceledQueue;
    private Timer recentlyChangedQueueTimer = new Timer("recentlyChangedQueueTimer", true);
    private ConcurrentLinkedQueue<RecentlyChangedItem> recentlyChangedQueue = new ConcurrentLinkedQueue<RecentlyChangedItem>();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock read  = readWriteLock.readLock();
    private final Lock write = readWriteLock.writeLock();

    protected InstanceRegistry() {
       
        _recentCanceledQueue = new CircularQueue<Pair<Long,String>>(1000);
        _recentRegisteredQueue = new CircularQueue<Pair<Long,String>>(1000);
        recentlyChangedQueueTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                Iterator<RecentlyChangedItem> it = recentlyChangedQueue.iterator();
                while (it.hasNext()) {
                    if (it.next().getLastUpdateTime() < System.currentTimeMillis() - TIME_MS_IN_RECENTLY_CHANGED_QUEUE) {
                        it.remove();
                    }
                    else {
                        break;
                    }
                }
            }
            
        }, RCQ_CLEANUP_TIMER, RCQ_CLEANUP_TIMER);
        try {
           
           DefaultMonitorRegistry.getInstance().register(Monitors.newObjectMonitor("1", this));
         
        } catch (Throwable e) {
            s_logger.warn(
                    "Cannot register the JMX monitor for the InstanceRegistry :"
                           , e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void register(InstanceInfo r, int leaseDuration, long clock, boolean isReplication) {
        try {
            read.lock();
            Map<String, Lease<InstanceInfo>> gMap = _registry.get(r.getAppName());
            REGISTER.increment(isReplication);
            if (gMap == null) {
                final ConcurrentHashMap<String, Lease<InstanceInfo>> gNewMap = new ConcurrentHashMap<String, Lease<InstanceInfo>>();
                gMap = _registry.putIfAbsent(r.getAppName(), gNewMap);
                if (gMap == null)
                	gMap = gNewMap;
            }
            Lease<InstanceInfo> existingLease = gMap.get(r.getId());
            Lease<InstanceInfo> lease = new Lease<InstanceInfo>(r, leaseDuration, clock);
            gMap.put(r.getId(), lease);
            synchronized (_recentRegisteredQueue) {
                _recentRegisteredQueue.add(new Pair<Long, String>(Long.valueOf(System.currentTimeMillis()),
                        r.getAppName() + "(" + r.getId() +")"));
            }
           // This is where the initial state transfer of overridden status happens
            if (!InstanceStatus.UNKNOWN.equals(r.getOverriddenStatus())) {
                s_logger.info(
                        "Found overridden status {} for instance {}. Adding to the instance map",
                        r.getOverriddenStatus(), r.getId());
                if (!overriddenInstanceStatusMap.containsKey(r.getId())) {
                    s_logger.info(
                            "Not found overridden id {} and hence adding it",
                            r.getId());
                    overriddenInstanceStatusMap.put(r.getId(), r.getOverriddenStatus());
                }
            }
            // Set the status based on the overridden status rules
            r.setStatus(getOverriddenInstanceStatus(r, existingLease, isReplication));
            
            if (r != null) {
                r.setActionType(ActionType.ADDED);
                recentlyChangedQueue.add(new RecentlyChangedItem(lease));
                r.setLastUpdatedTimestamp();
            }
            invalidateCache(r.getAppName());
            s_logger.info("Registered instance id {} with status {}", 
                    r.getId(), r.getStatus().toString());
                s_logger.info("DS: Registry: registered " +  r.getAppName() + " - " + r.getId());
            }
        finally {
            read.unlock();
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
            s_logger.info(
                    "Trusting the instance status {} from replica or instance for instance",
                    r.getStatus(), r.getId());
            return r.getStatus();
        }
        // Overrides are the status like OUT_OF_SERVICE and UP set by NAC
        InstanceStatus overridden = overriddenInstanceStatusMap.get(r.getId());
        // If there are instance specific overrides, then they win - otherwise
        // the ASG status
        if (overridden != null) {
            s_logger.info(
                    "The instance specific override for instance {} and the value is {}",
                    r.getId(), overridden.name());
            return overridden;
        }
        // If the ASGName is present- check for its status
        boolean isASGDisabled = false;
        if (r.getASGName() != null) {
            isASGDisabled = !AwsAsgUtil.getInstance().isASGEnabled(
                    r.getASGName());
            s_logger.info("The ASG name is specified {} and the value is {}",
                    r.getASGName(), isASGDisabled);
            if (isASGDisabled) {
                return InstanceStatus.OUT_OF_SERVICE;
            } else {
                return InstanceStatus.UP;
            }
        }
        // This is for backward compatibility until all applications have ASG names, otherwise while starting up
        // the client status may override status replicated from other servers
        if (!isReplication) {
            InstanceStatus existingStatus = null;
            if (existingLease != null) {
                existingStatus = existingLease.getHolder().getStatus();
            }
            // Allow server to have its way when the status is UP or OUT_OF_SERVICE
            if ((existingStatus != null)
                    && (InstanceStatus.OUT_OF_SERVICE.equals(existingStatus) || InstanceStatus.UP
                            .equals(existingStatus))) {
                s_logger.info(
                        "There is already an existing lease with status {}  for instance {}",
                        existingLease.getHolder().getStatus().name(),
                        existingLease.getHolder().getId());
                return existingLease.getHolder().getStatus();
            }
        }
        s_logger.info(
                "Returning the default instance status {} for instance {},",
                r.getStatus(), r.getId());
        return r.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    public boolean cancel(String appName, String id, long clock, boolean isReplication) {
       try {
           read.lock();
           CANCEL.increment(isReplication);
            Map<String, Lease<InstanceInfo>> gMap = _registry.get(appName);
            Lease<InstanceInfo> leaseToCancel = null;
            if(gMap != null){
                leaseToCancel = gMap.remove(id);
            }
            synchronized (_recentCanceledQueue) {
                _recentCanceledQueue.add(new Pair<Long, String>(Long.valueOf(System.currentTimeMillis()),
                        appName + "(" + id +")"));
            }
            InstanceStatus instanceStatus = overriddenInstanceStatusMap.remove(id);
            if (instanceStatus != null) {
                s_logger.info("Removed instance id {} from the overridden map which has value {}", id, instanceStatus.name());
            }
            if(leaseToCancel == null){
                CANCEL_NOT_FOUND.increment(isReplication);
                s_logger.warn("DS: Registry: cancel failed because Lease is not registered for: "+
                        appName + ":" + id);
                return false;
            }else {
                leaseToCancel.cancel(clock);
                InstanceInfo instanceInfo = leaseToCancel.getHolder();
                if (instanceInfo != null) {
                    instanceInfo.setActionType(ActionType.DELETED);
                    recentlyChangedQueue.add(new RecentlyChangedItem(leaseToCancel));
                    instanceInfo.setLastUpdatedTimestamp();
                }
                invalidateCache(appName);
                s_logger.info("DS: Registry: canceled lease: " + appName + " - " + id);
                return true;
            }
        }
        finally {
            read.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean renew(String appName, String id, long clock, boolean isReplication) {
        RENEW.increment(isReplication);
        Map<String, Lease<InstanceInfo>> gMap = _registry.get(appName);
        Lease<InstanceInfo> leaseToRenew = null;
        if(gMap != null){
            leaseToRenew = gMap.get(id);
        }
        if(leaseToRenew == null){
            RENEW_NOT_FOUND.increment(isReplication);
            s_logger.warn("DS: Registry: lease doesn't exist, registering resource: "+
                    appName + " - " + id);
            return false;
        }else {
            InstanceInfo instanceInfo = leaseToRenew.getHolder();
            if (instanceInfo != null) {
                //touchASGCache(instanceInfo.getASGName());
                InstanceStatus overriddenInstanceStatus = this
                        .getOverriddenInstanceStatus(instanceInfo,
                                leaseToRenew, isReplication);
        //InstanceStatus overriddenInstanceStatus = instanceInfo.getStatus();
                if ((!isReplication) && (!instanceInfo.getStatus().equals(overriddenInstanceStatus))) {
                    // This can happen if the NAC disables cluster, but the message is not reached to this server somehow
                    Object[] args = {instanceInfo.getStatus().name(), instanceInfo
                            .getOverriddenStatus().name(), instanceInfo
                            .getId()};
                    s_logger.info(
                            "The overridden instance status %s is different from instance status %s for instance %s. Hence setting the status to overriddentstatus",
                            args);
                   instanceInfo.setStatus(overriddenInstanceStatus);
                }
            }
            _renewsInLastMin.increment();
            leaseToRenew.renew(clock);
            return true;
        }
    }
   
    /**
     * Touches the ASG cache to indicate the cache is in use and cannot be expired
     * @param asgName - The ASG name for which the cache should not be expired
     */
    private void touchASGCache(String asgName) {
       if (asgName != null) {
           boolean isAsgEnabled = AwsAsgUtil.getInstance().isASGEnabled(asgName);
           s_logger.debug("The ASG value for asg {} is {}", asgName, isAsgEnabled);
       }
    }

    public void storeOverriddenStatusIfRequired(String id, InstanceStatus overriddenStatus) {
        InstanceStatus instanceStatus = overriddenInstanceStatusMap.get(id);
        if ((instanceStatus == null) || (!overriddenStatus.equals(instanceStatus))) {
            // We might not have the overridden status if the server got restarted -this will help us maintain the overridden state
            // from the replica
            s_logger.info("Adding overridden status for instance id {} and the value is {}", id, overriddenStatus.name());
            overriddenInstanceStatusMap.put(id, overriddenStatus);
        }
    }
    public boolean statusUpdate(String appName,
            String id,
            InstanceStatus newStatus,
            long clock,
            boolean isReplication) {
        
        try {
            read.lock();
            STATUS_UPDATE.increment(isReplication);
            Map<String, Lease<InstanceInfo>> gMap = _registry.get(appName);
            Lease<InstanceInfo> lease = null;
            if(gMap != null){
                lease = gMap.get(id);
            }
            if(lease == null){
                return false;
            }else {
                lease.renew(clock);
                lease.setLastStatusUpdateTimestamp(System.currentTimeMillis());
                InstanceInfo info = lease.getHolder();
                if ((info != null) && !(info.getStatus().equals(newStatus))) {
                    // This is NAC overriden status
                    overriddenInstanceStatusMap.put(id, newStatus);
                    // Set it for transfer of overridden status to replica on replica start up
                    info.setOverriddenStatus(newStatus);
                    info.setStatus(newStatus);
                    if (info != null) {
                        info.setActionType(ActionType.MODIFIED);
                        recentlyChangedQueue.add(new RecentlyChangedItem(lease));
                        info.setLastUpdatedTimestamp();
                    }
                    invalidateCache(appName);
                }
                return true;
            }
        }
        finally {
            read.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void evict() {

        if(! isLeaseExpirationEnabled()){
            s_logger.info("DS: lease expiration is currently disabled.");
            return;
        }
        s_logger.info("Running the evict task");
        for(Iterator<Entry<String, Map<String,Lease<InstanceInfo>>>> iter =
            _registry.entrySet().iterator(); iter.hasNext();) {
            Entry<String, Map<String,Lease<InstanceInfo>>> groupEntry = iter.next();

            Map<String,Lease<InstanceInfo>> leaseMap = groupEntry.getValue();
            if(leaseMap != null){
                for(Iterator<Entry<String, Lease<InstanceInfo>>> subIter =
                    leaseMap.entrySet().iterator(); subIter.hasNext();){
                    Entry<String, Lease<InstanceInfo>> leaseEntry = subIter.next();
                    Lease<InstanceInfo> lease = leaseEntry.getValue();
                    if(lease.isExpired() && lease.getHolder() != null){
                        String appName = lease.getHolder().getAppName();
                        String id = lease.getHolder().getId();
                        EXPIRED.increment();
                        s_logger.warn("DS: Registry: expired lease for " + appName + " - " + id);
                        cancel(appName, id, -1, false);
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Application getApplication(String appName) {
        Application app = null;

        Map<String,Lease<InstanceInfo>> leaseMap = _registry.get(appName);

        if(leaseMap != null && leaseMap.size() > 0){
            for(Iterator<Entry<String, Lease<InstanceInfo>>> iter = leaseMap.entrySet().iterator();
                iter.hasNext();){
                Entry<String,Lease<InstanceInfo>> entry = iter.next();

                if(isLeaseExpirationEnabled() && entry.getValue().isExpired()){
                    continue;
                }

                if(app == null){
                    app = new Application(appName);
                }
                app.addInstance(decorateInstanceInfo(entry.getValue()));
            }
        }

        return app;
    }

    /**
     * {@inheritDoc}
     */
    public Applications getApplications() {
        GET_ALL_CACHE_MISS.increment();
        Applications apps = new Applications();
        apps.setVersion(1L);
        for(Iterator<Entry<String, Map<String,Lease<InstanceInfo>>>> iter =
            _registry.entrySet().iterator(); iter.hasNext();) {
            Entry<String,Map<String,Lease<InstanceInfo>>> entry = iter.next();

            Application app = null;

            if(entry.getValue() != null) {
                for(Iterator<Entry<String,Lease<InstanceInfo>>> subIter =
                    entry.getValue().entrySet().iterator(); subIter.hasNext();) {

                    Lease<InstanceInfo> lease = subIter.next().getValue();
                    
                    if(app == null){
                        app = new Application(lease.getHolder().getAppName());
                    }

                    app.addInstance(decorateInstanceInfo(lease));
                }
            }
            if(app != null){
                apps.addApplication(app);
            }
        }
        apps.setAppsHashCode(apps.getReconcileHashCode());
        return apps;
    }
    
    /**
     * {@inheritDoc}
     */
    public Applications getApplicationDeltas() {
        GET_ALL_CACHE_MISS_DELTA.increment();
        Applications apps = new Applications();
        apps.setVersion(ResponseCache.getVersionDelta().get());
        Map<String, Application> applicationInstancesMap = new HashMap<String, Application>();
        try {
            write.lock();
            Iterator<RecentlyChangedItem> iter =
                this.recentlyChangedQueue.iterator();
            s_logger.info("The number of elements in the delta queue is :" + this.recentlyChangedQueue.size());
            while( iter.hasNext()) {
               Lease<InstanceInfo> lease = iter.next().getLeaseInfo();
               InstanceInfo instanceInfo = lease.getHolder();
               Object[] args = { instanceInfo.getId(), instanceInfo.getStatus().name(), instanceInfo.getActionType().name()};
               s_logger.debug("The instance id %s is found with status %s and actiontype %s", 
                       args);
               Application app = applicationInstancesMap.get(instanceInfo.getAppName());
               if (app == null) {
                   app = new Application(instanceInfo.getAppName());
                   applicationInstancesMap.put(instanceInfo.getAppName(), app);
                   apps.addApplication(app);
               }
               app.addInstance(decorateInstanceInfo(lease));
            }
            Applications allApps = getApplications();
            apps.setAppsHashCode(allApps.getReconcileHashCode());
            return apps;
        }
        finally {
            write.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public InstanceInfo getInstanceByAppAndId(String appName, String id) {
        Map<String,Lease<InstanceInfo>> leaseMap = _registry.get(appName);
        Lease<InstanceInfo> lease = null;
        if(leaseMap != null){
           lease = leaseMap.get(id);
        }
        if(lease != null &&
                (!isLeaseExpirationEnabled() || !lease.isExpired())){
            return decorateInstanceInfo(lease);
        }else {
            return null;
        }
    }


    /**
     * {@inheritDoc}
     */
    public List<InstanceInfo> getInstancesById(String id) {
        List<InstanceInfo> list = Collections.emptyList();

        for(Iterator<Entry<String, Map<String,Lease<InstanceInfo>>>> iter =
            _registry.entrySet().iterator(); iter.hasNext();) {

            Map<String,Lease<InstanceInfo>> leaseMap = iter.next().getValue();
            if(leaseMap != null) {
                Lease<InstanceInfo> lease = leaseMap.get(id);

                if(lease == null || (isLeaseExpirationEnabled() && lease.isExpired())) {
                    continue;
                }

                if(lease != null && list == Collections.EMPTY_LIST) {
                    list = new ArrayList<InstanceInfo>();
                }

                if(lease != null){
                    list.add(decorateInstanceInfo(lease));
                }
            }
        }
        return list;
    }

    /**
     * http://jira/browse/PDCLOUD-412
     * Discovery: clients' leases are expiring on DS node that just started because it's
     * taking clients minutes to resolve to the new instance via EIP
     */
    public abstract boolean isLeaseExpirationEnabled();

    private InstanceInfo decorateInstanceInfo(Lease<InstanceInfo> lease) {
        InstanceInfo info = lease.getHolder();

        //client app settings
        int renewalInterval = LeaseInfo.DEFAULT_LEASE_RENEWAL_INTERVAL;
        int leaseDuration = LeaseInfo.DEFAULT_LEASE_DURATION;

        //TODO: clean this up
        if(info.getLeaseInfo() != null) {
            renewalInterval = info.getLeaseInfo().getRenewalIntervalInSecs();
            leaseDuration = info.getLeaseInfo().getDurationInSecs();
        }

        info.setLeaseInfo(LeaseInfo.Builder.newBuilder().
                setRegistrationTimestamp(lease.getRegistrationTimestamp()).
                setRenewalTimestamp(lease.getLastRenewalTimestamp()).
                setClock(lease.getClock()).
                setRenewalIntervalInSecs(renewalInterval).
                setDurationInSecs(leaseDuration).
                setEvictionTimestamp(lease.getEvictionTimestamp()).build());

        info.setIsCoordinatingDiscoveryServer();
        return info;
    }


    @com.netflix.servo.annotations.Monitor(name="numOfRenewsInLastMin",
            description="Number of total heartbeats received in the last minute",
            type=DataSourceType.GAUGE
          )
    public long getNumOfRenewsInLastMin() {
        return _renewsInLastMin.getCount();
    }

    public List<Pair<Long, String>> getLastNRegisteredInstances() {
        List<Pair<Long, String>> list = new ArrayList<Pair<Long,String>>();

        synchronized (_recentRegisteredQueue) {
            for(Iterator<Pair<Long, String>> iter = _recentRegisteredQueue.iterator(); iter.hasNext();) {
                list.add(iter.next());
            }
        }
        Collections.reverse(list);
        return list;
    }

    public List<Pair<Long, String>> getLastNCanceledInstances() {
        List<Pair<Long, String>> list = new ArrayList<Pair<Long,String>>();
        synchronized (_recentCanceledQueue) {
            for(Iterator<Pair<Long, String>> iter = _recentCanceledQueue.iterator(); iter.hasNext();) {
                list.add(iter.next());
            }
        }
        Collections.reverse(list);
        return list;
    }

    private void invalidateCache(String appName) {
        //invalidate cache
        ResponseCache.getInstance().invalidate(appName);
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
    _renewsInLastMin = new MeasuredRate(1000 * 60 * 1);
    _scheduledEvictionTimer.schedule(new TimerTask(){

        @Override
        public void run() {
            try {
                evict();
            }
            catch (Throwable e) {
                s_logger.error("Could not run the evict task", e);
            }
            
        }}, EVICTION_DELAY_IN_MS, EVICTION_DELAY_IN_MS);
    }
    
    
    @com.netflix.servo.annotations.Monitor(name="numOfElementsinInstanceCache",
            description="Number of elements in the instance Cache",
            type=DataSourceType.GAUGE
            )
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
    
}
