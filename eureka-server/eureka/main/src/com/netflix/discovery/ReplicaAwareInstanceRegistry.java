/*
 * ReplicaAwareInstanceRegistry.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.ObjectName;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ComputationException;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.cluster.ReplicaNode;
import com.netflix.discovery.lease.Lease;
import com.netflix.discovery.resources.ASGResource.ASGStatus;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import com.netflix.discovery.util.DSCounter;
import com.netflix.discovery.util.MeasuredRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.DataSourceLevel;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * {@link InstanceRegistry} that is aware of their replicas
 * 
 * @author gkim
 */
public class ReplicaAwareInstanceRegistry extends InstanceRegistry 
    implements ReplicaAwareInstanceRegistryMBean{

    private static final String DISCOVERY_APP_NAME = "DISCOVERY";

    private static final String NETFLIX_DISCOVERY_SELF_PRESERVATION_MODE = "netflix.discovery.selfPreservationMode";

    private static final DynamicBooleanProperty SELF_PRESERVATION_MODE_PROPERTY = DynamicPropertyFactory.getInstance().getBooleanProperty(NETFLIX_DISCOVERY_SELF_PRESERVATION_MODE, true);

    private static final int REPLICA_NODES_UPDATE_INTERVAL_MS = 10*60*1000;

    private static final int RENEW_THRESHOLD_UPDATER_INTERVAL_MS = 15* 60*1000;

    private static final String DICOVERY_FAILED_REPLICATION_AFTER_RETRY = "Dicovery:FailedReplicationAfterRetry";

    private static final int REPL_RETRY_SLEEP_TIME_IN_MS = 40;

    private static final int REFRESH_DISCOVERY_INSTANCE_LIST_IN_MS = REPLICA_NODES_UPDATE_INTERVAL_MS;

    private static final Logger s_logger = LoggerFactory.getLogger(ReplicaAwareInstanceRegistry.class); 

    //http://jira/browse/PDCLOUD-412
    private static final double RENEW_PERCENT_THRESHOLD = 0.85;
    private static final com.netflix.config.DynamicIntProperty PROP_RETRY_REPLICATION_COUNTER = DynamicPropertyFactory.getInstance().getIntProperty("netflix.discovery.replicationRetry", 5);

    private static final DynamicBooleanProperty PROP_REPL_IF_UP_IN_DISCOVERY = DynamicPropertyFactory.getInstance().getBooleanProperty("netflix.discovery.replicateOnlyIfUpinDiscovery", true);
    
    private long startupTime = 0;
    
    private boolean replicaTransferEmptyOnStartup = true;
    
    private static final Timer timerReplicaNodes = new Timer("ReplicaNodesUpdater", true);
    enum Action {Heartbeat, Register, Cancel, StatusUpdate}
    

    private final static Comparator<Application> APP_COMPARATOR =
            new Comparator<Application>() {
            public int compare(Application l, Application r) {
                return l.getName().compareTo(r.getName());
            }
        };
        
    /**
     * Holder for the singleton instance of the library.
     */
    private static final class Holder {
        private static ReplicaAwareInstanceRegistry s_instanceRegistry = 
            new ReplicaAwareInstanceRegistry();
        private Holder() { }
        static ReplicaAwareInstanceRegistry getInstance() {
            return s_instanceRegistry;
        }
    }

    private final AtomicLong _clock = new AtomicLong(1);
    
    private final MeasuredRate _numOfReplicationsInLastMin = new MeasuredRate(1000 * 60 * 1);
    private final ThreadPoolExecutor _replicationService;
    
    private int _totalDSNodes;
    
    //As EIP mapping sometimes like longer than desired.  Enable lease expiration 
    //only when numOfRenewsPerMinThreshold is exceeded.
    private volatile int _numOfRenewsPerMinThreshold;
    private ObjectName _objectName;
    
    private AtomicReference<List<ReplicaNode>> _replicaNodes;

    private Timer timer = new Timer("RenewalThresholdUpdater", true);
    private static final LoadingCache<String, Boolean> discoveryInstancesCache = CacheBuilder.newBuilder()
    .initialCapacity(10)
    .expireAfterWrite(REFRESH_DISCOVERY_INSTANCE_LIST_IN_MS,
            TimeUnit.MILLISECONDS)
            .<String, Boolean>build(
                    new CacheLoader<String, Boolean>() {
                        public Boolean load(String serviceUrl) {
                            Stopwatch t = Monitors.newTimer("DISCOVERY:getDiscoveryNodes").start();
                            try {
                                return isReplicaAliveInMyRegistry(serviceUrl);
                            } catch (Throwable e) {
                                throw new ComputationException(e);
                            }
                            finally {
                                t.stop();
                            }
                        }

                    });

    private static final com.netflix.config.DynamicIntProperty STARTUP_WAIT_TIME_MS = DynamicPropertyFactory.getInstance().getIntProperty("netflix.discovery.waitTimeonEmptySync", 1000*60*5);
    private static ConcurrentMap<String, Boolean> discoveryInstancesMap = new ConcurrentHashMap<String, Boolean>() {

        private static final long serialVersionUID = 1L;

        @Override
        public Boolean get(Object key) {
            String myKey = (String)key;
            try {
                return discoveryInstancesCache.get(myKey);
            } catch (ExecutionException e) {
                throw new RuntimeException("Cannot get other discovery instances ", e);
            }
        }
    };;
  
    ReplicaAwareInstanceRegistry() {
        _replicaNodes = new AtomicReference<List<ReplicaNode>>();
        _replicaNodes.set(new ArrayList<ReplicaNode>());
        try {
            
            DefaultMonitorRegistry.getInstance().register(Monitors.newObjectMonitor("1", this));
          
         } catch (Throwable e) {
             s_logger.warn(
                     "Cannot register the JMX monitor for the InstanceRegistry :"
                            , e);
         }
         ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(false).setNameFormat("replication.pool").build();
        _replicationService  = new ThreadPoolExecutor(20, 60, 15, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(120), threadFactory){};
            
            //
            /*
            new NFExecutorPool("replication.pool",
                20, /* corePoolSize */
                //60, /* max threads */
                //60, /* idle */
                /*
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(120));
                */
        init();
     }

    public static ReplicaAwareInstanceRegistry getInstance() {
        return Holder.getInstance();
    }

    /**
     * Assumes platform has already been initialized
     */
    private void init() {
        
        try{
            updateReplicaNodes();
            timerReplicaNodes.schedule(new TimerTask() {

                @Override
                public void run() {
                   try {
                       updateReplicaNodes();
                   }
                   catch (Throwable e) {
                       s_logger.error("Cannot update the replica Nodes", e);
                   }
                    
                }}, REPLICA_NODES_UPDATE_INTERVAL_MS, REPLICA_NODES_UPDATE_INTERVAL_MS);
 
        }catch(Exception e){
            throw new IllegalStateException(e);
        }
        timer.schedule(
                new TimerTask() {

                    @Override
                    public void run() {
                        updateRenewalThreshold();

                    }

 
                }, RENEW_THRESHOLD_UPDATER_INTERVAL_MS,
                RENEW_THRESHOLD_UPDATER_INTERVAL_MS);
        
        DefaultMonitorRegistry.getInstance().register(Monitors.newObjectMonitor("1", this));

    }

    private void updateReplicaNodes() {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        List<String> replicaUrls = DiscoveryClient.getDiscoveryServiceUrls(DiscoveryClient.getZone(myInfo));
        _totalDSNodes = replicaUrls.size();
        List<ReplicaNode> replicaNodes = new ArrayList<ReplicaNode>();
        int ctr = 0;
        for(String replicaUrl : replicaUrls){
             if(! isThisMe(replicaUrl)) {
                s_logger.info("Adding replica node: " + replicaUrl);
                replicaNodes.add(new ReplicaNode(replicaUrl, ++ctr));           
            }
        }
        if (replicaNodes.isEmpty()) {
            s_logger.warn("The replica size seems to be empty. Check the route 53 DNS Registry");
            return;
        }
        if (!replicaNodes.equals(_replicaNodes.get())) {
            s_logger.info(
                    "Updating the replica nodes as they seem to have changed from {} to {} ",
                    _replicaNodes.get(), replicaNodes);
       _replicaNodes.set(replicaNodes);
        }
    }

    public void syncUp() {
        //Copy entire entry from neighboring DS node
        LookupService lookupService = 
            DiscoveryManager.getInstance().getLookupService();
    
        Applications apps = lookupService.getApplications();
        int count = 0;
        for(Application app : apps.getRegisteredApplications()){
            for(InstanceInfo instance : app.getInstances()){
                try{
                    register(instance, -1, true);
                    count++;
                }catch(Throwable t){
                    s_logger.error("During DS init copy", t);
                }
            }
        }
        _numOfRenewsPerMinThreshold = (int) ((count*2) * RENEW_PERCENT_THRESHOLD);
        s_logger.info("Got " + count + " instances from neighboring DS node.  Changing status to UP.");
        s_logger.info("Renew threshold is: " + _numOfRenewsPerMinThreshold);
        this.startupTime = System.currentTimeMillis();
        if (count > 0) {
            this.replicaTransferEmptyOnStartup = false;
        }
        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceStatus.UP);
        super.postInit();
    }
    
    /**
     * Checks to see if the registry access is allowed. 
     * @return false - if the instances count from a replica transfer returned zero and if the wait time has not elapsed, o
     * otherwise returns true
     */
    public boolean shouldAllowAccess() {
        if (this.replicaTransferEmptyOnStartup) {
            if (System.currentTimeMillis() > this.startupTime + STARTUP_WAIT_TIME_MS.get()) {
                return true;
            }
            else {
                return false;
            }
        }
        return true;
    }
   
    public List<ReplicaNode> getReplicaNodes() {
        return Collections.unmodifiableList(_replicaNodes.get());
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean cancel(final String appName, 
            final String id, 
            final long clock, 
            final boolean isReplication) {
       return replicate(Action.Cancel, appName, id, null, null, clock, isReplication);
    }

    /**
     * {@inheritDoc}
     */
    public void register(final InstanceInfo info,
                         final long clock,
                         final boolean isReplication) {
        replicate(Action.Register, 
                info.getAppName(), 
                info.getId(), 
                info,
                null,
                clock, 
                isReplication);
    }

    /**
     * {@inheritDoc}
     */
    public boolean renew(final String appName, 
            final String id, 
            final long clock, 
            final boolean isReplication) {
        return replicate(Action.Heartbeat, 
                appName, 
                id, 
                null,
                null,
                clock, 
                isReplication);
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean statusUpdate(final String appName, 
            final String id,
            final InstanceStatus newStatus,
            final long clock, 
            final boolean isReplication) {
        return replicate(Action.StatusUpdate, 
                appName, 
                id, 
                null,
                newStatus,
                clock, 
                isReplication);
    }
    
    /**
     * {@inheritDoc}
     */
    public void statusUpdate(final String asgName, final ASGStatus newStatus,
            final boolean isReplication) {
        // If this is replicated from an other node, do not try to replicate
        // again
        if (isReplication) {
            return;
        }
        for (final ReplicaNode node : _replicaNodes.get()) {
            String serviceUrl = node.getServiceUrl();
            if (!isDiscoveryInstanceAlive(serviceUrl)
                    && PROP_REPL_IF_UP_IN_DISCOVERY.get()) {
                s_logger.warn(
                        "The discovery node {} seems to be down and hence not replicating it there",
                        serviceUrl);
            }

            try {
                _replicationService.execute(new Runnable() {
                    public void run() {
                        boolean success = false;
                        int retryCounter = PROP_RETRY_REPLICATION_COUNTER.get();
                        int ctr = 0;
                        CurrentRequestVersion.set(Version.V2);
                        while ((!success) && (ctr++ < retryCounter)) {
                            try {
                                if (node.statusUpdate(asgName, newStatus)) {
                                    node.getStatus().setSuccess();
                                    success = true;
                                } else {
                                    Thread.sleep(REPL_RETRY_SLEEP_TIME_IN_MS);
                                }
                            } catch (Throwable e) {
                                s_logger.error(
                                        "ReplicaAwareInstanceRegistry: ASGStatusUpdate",
                                        e);
                                DSCounter.FAILED_REPLICATIONS.increment();
                                node.getStatus().setFailure(e);
                                try {
                                    Thread.sleep(REPL_RETRY_SLEEP_TIME_IN_MS);
                                } catch (InterruptedException e1) {
                                }

                            }

                        }

                    }

                });
            } catch (RejectedExecutionException e) {
                s_logger.error("ReplicaAwareInstanceRegistry: RejectedExecutionException: ASGStatusUpdate "
                        + " - " + node.getServiceUrl());
                DSCounter.REJECTED_REPLICATIONS.increment();
                node.getStatus().setFailure(e);
            } catch (Throwable t) {
                s_logger.error("ReplicaAwareInstanceRegistry: ASGStatusUpdate",
                        t);
                DSCounter.FAILED_REPLICATIONS.increment();
                node.getStatus().setFailure(t);

            }
        }

    }
  
    public void shutdown() {
        try {
            _replicationService.shutdown();
            DefaultMonitorRegistry.getInstance().unregister(Monitors.newObjectMonitor("1", this));
        }catch(Throwable t){
            s_logger.error("Error unregistering", t);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLeaseExpirationEnabled() {
        boolean leaseExpirationEnabled = (getNumOfRenewsInLastMin() > _numOfRenewsPerMinThreshold);    
        boolean isSelfPreservationModeEnabled = isSelfPreservationModeEnabled();
        if ((!leaseExpirationEnabled)) {
            if (isSelfPreservationModeEnabled) {
                s_logger.error("The lease expiration has been disabled since the number of renewals per minute  "
                        + " is lower than the minimum threshold. Number of Renewals Last Minute : "
                        + getNumOfRenewsInLastMin()
                        + ". The Threshold is "
                        + RENEW_PERCENT_THRESHOLD
                        + " of total instances : "
                        + _numOfRenewsPerMinThreshold);
            } else {
                s_logger.warn("The self preservation mode is disabled!. Hence allowing the instances to expire.");
                leaseExpirationEnabled = true;
        }
        }
        return leaseExpirationEnabled;
    }
    
    public boolean isSelfPreservationModeEnabled() {
        return SELF_PRESERVATION_MODE_PROPERTY.get();
    }
    
    /**
     * Update threshold every 15 mins. Update threshold only if we haven't lost 15% of the instances
     * in the last 15 mins.
     */
    private void updateRenewalThreshold() {
        try {
            LookupService lookupService = DiscoveryManager
                    .getInstance().getLookupService();
            Applications apps = lookupService.getApplications();
            int count = 0;
            for (Application app : apps
                    .getRegisteredApplications()) {
                for (InstanceInfo instance : app.getInstances()) {
                    ++count;
                }
            }
            // Update threshold only if in the last min, 85% of the instances are healthy
            if ((count * 2) > (RENEW_PERCENT_THRESHOLD * _numOfRenewsPerMinThreshold )) {
                _numOfRenewsPerMinThreshold = (int) ((count * 2) * RENEW_PERCENT_THRESHOLD);
                s_logger.info("Updated the renewal threshold to : {}",  _numOfRenewsPerMinThreshold);
            }
        } catch (Throwable e) {
            s_logger.error("Cannot update renewal threshold", e);
        }
    }
    
    private boolean replicate(final Action action, final String appName,
            final String id, final InstanceInfo info /* optional */,
            final InstanceStatus newStatus /* optional */, final long clock,
            final boolean isReplication) {

        boolean rc;
        Stopwatch tracer = Monitors.newTimer("DS: ReplicaAware: "
                + action.name()).start();
        try {
            if (action == Action.Cancel) {
                rc = super.cancel(appName, id, clock, isReplication);
            } else if (action == Action.Heartbeat) {
                rc = super.renew(appName, id, clock, isReplication);
            } else if (action == Action.StatusUpdate) {
                rc = super.statusUpdate(appName, id, newStatus, clock,
                        isReplication);
            } else {
                int leaseDuration = Lease.DEFAULT_DURATION_IN_SECS;
                if (info.getLeaseInfo() != null
                        && info.getLeaseInfo().getDurationInSecs() > 0) {
                    leaseDuration = info.getLeaseInfo().getDurationInSecs();
                }
                super.register(info, leaseDuration, clock, isReplication);
                rc = true;
            }

            if (isReplication) {
                _numOfReplicationsInLastMin.increment();
            }

            if (_replicaNodes == Collections.EMPTY_LIST || isReplication) {
                return rc;
            }

            // Currently when auto-scaler replaces discovery nodes, it re-maps
            // the public
            // ip to an eip *after* nodes have started. DiscoveryClient has
            // logic to
            // detect this automatically rename it's public dns name/ip. This is
            // logic
            // to check periodically to see if we are unnecessarily replicating
            // to ourselves
            // because of a recent eip remap.
            if ((_clock.incrementAndGet() % 5) == 0
                    && _replicaNodes.get().size() >= _totalDSNodes) {
                for (int i = 0; i < _replicaNodes.get().size(); i++) {
                    String url = _replicaNodes.get().get(i).getServiceUrl();
                    if (isThisMe(url)) {
                        Monitors.newCounter("DS:ReplicaInvalid");
                        _replicaNodes.get().remove(i);
                        break;
                    }
                }
            }
            for (final ReplicaNode node : _replicaNodes.get()) {
                try {
                    _replicationService.execute(new Runnable() {
                        public void run() {
                            int retryCounter = PROP_RETRY_REPLICATION_COUNTER.get();
                            replicateToReplicaNodes(action, appName, id, info,
                                    newStatus, clock, node, retryCounter);
                        }

                    });
                } catch (RejectedExecutionException e) {
                    s_logger.error("ReplicaAwareInstanceRegistry: RejectedExecutionException: "
                            + action + " - " + node.getServiceUrl());
                    DSCounter.REJECTED_REPLICATIONS.increment();
                    node.getStatus().setFailure(e);
                } catch (Throwable t) {
                    s_logger.error("ReplicaAwareInstanceRegistry: " + action, t);
                    DSCounter.FAILED_REPLICATIONS.increment();
                    node.getStatus().setFailure(t);
                }
            }
            return rc;

        } finally {
            tracer.stop();
        }
    }
    
    public List<Application> getSortedApplications() {
        List<Application> apps = new ArrayList<Application>(getApplications().getRegisteredApplications());
        Collections.sort(apps, APP_COMPARATOR);
        return apps;
    }
    
    @com.netflix.servo.annotations.Monitor(name="numOfReplicationsInLastMin",
            description="Number of total replications received in the last minute",
            type = com.netflix.servo.annotations.DataSourceType.GAUGE
            )
    public long getNumOfReplicationsInLastMin() {
        return _numOfReplicationsInLastMin.getCount();
    }
    
    @com.netflix.servo.annotations.Monitor(name="isBelowRenewThreshold",
            description="0 = false, 1 = true",
            type=com.netflix.servo.annotations.DataSourceType.GAUGE)
    public int isBelowRenewThresold() {
        if((getNumOfRenewsInLastMin() < _numOfRenewsPerMinThreshold) && 
                ((this.startupTime > 0) && (System.currentTimeMillis() > this.startupTime + (15*60*1000)))){
            return 1;
        }else {
            return 0;
        }
    }
    
    
    @com.netflix.servo.annotations.Monitor(name="numOfRenewsPerMinThreshold", type=DataSourceType.GAUGE)
    public int getNumOfRenewsPerMinThreshold() {
        return _numOfRenewsPerMinThreshold;
    }

    @com.netflix.servo.annotations.Monitor(name="itemsInReplicationPipeline", type=DataSourceType.GAUGE)
    public long getNumOfItemsInReplicationPipeline() {
        return _replicationService.getQueue().size();
    }
    
    @com.netflix.servo.annotations.Monitor(name="numOfActiveThreadsInReplicationPipeline", type=DataSourceType.GAUGE)
    public long getNumOfActiveThreadsInReplicationPipeline() {
        return _replicationService.getActiveCount();
    }
    
    private boolean isThisMe(String url) {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        if (((AmazonInfo)myInfo.getDataCenterInfo()).get(MetaDataKey.publicHostname) == null) {
            return true;
        }
        try {
            URI uri = new URI(url);
            return(myInfo.getDataCenterInfo().getName() == Name.Amazon &&
                   ((AmazonInfo)myInfo.getDataCenterInfo()).
                    get(MetaDataKey.publicHostname).equalsIgnoreCase(uri.getHost()));
        }catch(URISyntaxException e){
            s_logger.error("Error in syntax", e);
            return false;
        }
    }

    public void setNumOfRenewsPerMinThreshold(int newThreshold) {
        _numOfRenewsPerMinThreshold = newThreshold;
    }
    
    
    private void replicateToReplicaNodes(final Action action,
            final String appName, final String id, final InstanceInfo info,
            final InstanceStatus newStatus, final long clock,
            final ReplicaNode node, int retryCounter) {
       String serviceUrl = node.getServiceUrl();
       try {
           if (!isDiscoveryInstanceAlive(serviceUrl)
                   && PROP_REPL_IF_UP_IN_DISCOVERY.get()
                   && !("DISCOVERY".equals(appName))) {
               // Do not retry
               retryCounter = 0;
               s_logger.warn(
                       "The discovery node {} seems to be down and hence not replicating it there",
                       serviceUrl);
               node.clearStatusQueue();
               return;
           }
           CurrentRequestVersion.set(Version.V2);
           switch (action) {
            case Cancel:
                node.cancel(appName, id, clock);
                break;
            case Heartbeat:
                InstanceStatus overriddenStatus = overriddenInstanceStatusMap.get(id);
                InstanceInfo infoFromRegistry = getInstanceByAppAndId(appName,
                        id);
                if (!node.heartbeat(appName, id, clock, infoFromRegistry, overriddenStatus)) {
                    s_logger.info(
                            "Cannot find instance id {} and hence replicating the instance with status {}",
                            infoFromRegistry.getId(), infoFromRegistry
                            .getStatus().toString());
                    if (infoFromRegistry != null) {
                        node.register(infoFromRegistry, clock);
                    } else {
                        s_logger.error("ReplicaAwareInstanceRegistry: renew: missing entry!");
                    }
                }
                break;
            case Register:
                node.register(info, clock);
                break;
            case StatusUpdate:
                node.statusUpdate(appName, id, newStatus, clock);
                break;
            }
            node.getStatus().setSuccess();
        } catch (Throwable t) {
            Object[] args = {action.name(), serviceUrl,id, retryCounter--};
            s_logger.warn(
                    "ReplicaAwareInstanceRegistry: Failed replicating action %s for the server %s and instance id %s. Counting down from retry attempt %s ",
                    args);
            DSCounter.FAILED_REPLICATIONS.increment();
            if (retryCounter > 0) {
                try {
                    Thread.sleep(REPL_RETRY_SLEEP_TIME_IN_MS);
                } catch (InterruptedException ignore) {
                }
                replicateToReplicaNodes(action, appName, id, info, newStatus,
                        clock, node, retryCounter);
            } else {
                Monitors.newCounter(DICOVERY_FAILED_REPLICATION_AFTER_RETRY).increment();
                Object[] args_1 = {action.name(), serviceUrl, id};
                s_logger.error(
                        "ReplicaAwareInstanceRegistry: Failed replicating action {} for the server {} and instance id {}. No more retries left.",
                        args_1);

            }

        }
    }
    
    /**
     * Check if a discovery instance is alive
     * @param serviceUrl - The service url of the discovery node
     * @return - true, if alive, false otherwise
     */
    private boolean isDiscoveryInstanceAlive(String serviceUrl) {
        Stopwatch t = Monitors.newTimer("DISCOVERY:checkReplicaAlive").start();
        boolean isReplicaAlive = discoveryInstancesMap.get(serviceUrl);
        try {
            if (!isReplicaAlive) {
                return isReplicaAliveInMyRegistry(
                        serviceUrl);
            }
            return isReplicaAlive;
        }
        catch (Throwable e) {
            return true;
        }
        finally {
            t.stop();
        }
    }
   

    private static boolean isReplicaAliveInMyRegistry(
            String serviceUrl) throws URISyntaxException {
        URI uri = new URI(serviceUrl);
        Application app = ReplicaAwareInstanceRegistry.getInstance().getApplication(DISCOVERY_APP_NAME);
        List<InstanceInfo> instanceInfoList = app
        .getInstances();
        for (InstanceInfo instanceInfo : instanceInfoList) {
            if (((AmazonInfo) instanceInfo.getDataCenterInfo())
                    .get(MetaDataKey.publicHostname)
                    .equalsIgnoreCase(uri.getHost())) {
                return true;
            }
        }
        return false;
    }
    
    
}
