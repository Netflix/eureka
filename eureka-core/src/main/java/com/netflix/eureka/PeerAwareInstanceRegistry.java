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
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ComputationException;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.lease.Lease;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.eureka.util.EurekaMonitors;
import com.netflix.eureka.util.MeasuredRate;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;

/**
 * Handles replication of all operations to {@link InstanceRegistry} to peer
 * <em>Eureka</em> nodes to keep them all in sync.
 * 
 * <p>
 * Primary operations that are replicated are the
 * <em>Registers,Renewals,Cancels,Expirations and Status Changes</em>
 * </p>
 * 
 * <p>
 * When the eureka server starts up it tries to fetch all the registry
 * information from the peer eureka nodes.If for some reason this operation
 * fails, the server does not allow the user to get the registry information for
 * a period specified in
 * {@link EurekaServerConfig#getWaitTimeInMsWhenSyncEmpty()}.
 * </p>
 * 
 * <p>
 * One important thing to note about <em>renewals</em>.If the renewal drops more
 * than the specified threshold as specified in
 * {@link EurekaServerConfig#getRenewalPercentThreshold()} within a period of
 * {@link EurekaServerConfig#getRenewalThresholdUpdateIntervalMs()}, eureka
 * perceives this as a danger and stops expiring instances.
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
public class PeerAwareInstanceRegistry extends InstanceRegistry {
    private static final Logger logger = LoggerFactory
    .getLogger(PeerAwareInstanceRegistry.class);

    private static final EurekaServerConfig eurekaServerConfig = EurekaServerConfigurationManager
    .getInstance().getConfiguration();
    private static final String DICOVERY_FAILED_REPLICATION_AFTER_RETRY = "FailedReplicationAfterRetry";

    private static final int REPL_RETRY_SLEEP_TIME_IN_MS = 40;

    private long startupTime = 0;

    private boolean peerInstancesTransferEmptyOnStartup = true;

    private static final Timer timerReplicaNodes = new Timer(
            "Eureka-PeerNodesUpdater", true);

    enum Action {
        Heartbeat, Register, Cancel, StatusUpdate;

        private com.netflix.servo.monitor.Timer timer = Monitors.newTimer(this
                .name());

        public com.netflix.servo.monitor.Timer getTimer() {
            return this.timer;
        }

    }

    private final static Comparator<Application> APP_COMPARATOR = new Comparator<Application>() {
        public int compare(Application l, Application r) {
            return l.getName().compareTo(r.getName());
        }
    };

    private final MeasuredRate numberOfReplicationsLastMin = new MeasuredRate(
            1000 * 60 * 1);
    private final ThreadPoolExecutor replicationExecutorPool;

    private volatile int numberOfRenewsPerMinThreshold;

    private AtomicReference<List<PeerEurekaNode>> peerEurekaNodes;

    private Timer timer = new Timer(
            "ReplicaAwareInstanceRegistry - RenewalThresholdUpdater", true);
    private static final LoadingCache<String, Boolean> peerEurekaStatusCache = CacheBuilder
    .newBuilder()
    .initialCapacity(10)
    .expireAfterWrite(
            eurekaServerConfig
            .getPeerEurekaStatusRefreshTimeIntervalMs(),
            TimeUnit.MILLISECONDS)
            .<String, Boolean> build(new CacheLoader<String, Boolean>() {
                public Boolean load(String serviceUrl) {
                    try {
                        return isPeerAliveInMyRegistery(serviceUrl);
                    } catch (Throwable e) {
                        throw new ComputationException(e);
                    }
                }

            });

    private static ConcurrentMap<String, Boolean> peerEurekaStatusMap = new ConcurrentHashMap<String, Boolean>() {

        private static final long serialVersionUID = 1L;

        @Override
        public Boolean get(Object key) {
            String myKey = (String) key;
            try {
                return peerEurekaStatusCache.get(myKey);
            } catch (ExecutionException e) {
                throw new RuntimeException(
                        "Cannot get other discovery instances ", e);
            }
        }
    };
    private static final PeerAwareInstanceRegistry instance = new PeerAwareInstanceRegistry();

    private Counter failedReplicationAfterRetry = Monitors
    .newCounter(DICOVERY_FAILED_REPLICATION_AFTER_RETRY);

    PeerAwareInstanceRegistry() {
        // Make it an atomic reference since this could be updated in the
        // background.
        peerEurekaNodes = new AtomicReference<List<PeerEurekaNode>>();
        peerEurekaNodes.set(new ArrayList<PeerEurekaNode>());
        try {
            Monitors.registerObject(this);
        } catch (Throwable e) {
            logger.warn(
                    "Cannot register the JMX monitor for the InstanceRegistry :",
                    e);
        }
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(false).setNameFormat("Eureka-Replication-Thread")
        .build();
        // Thread pool used for replication
        replicationExecutorPool = new ThreadPoolExecutor(eurekaServerConfig
                .getMinThreadsForReplication(), eurekaServerConfig
                .getMaxThreadsForReplication(), eurekaServerConfig
                .getMaxIdleThreadAgeInMinutesForReplication(),
                TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(
                        eurekaServerConfig.getMaxElementsInReplicationPool()),
                        threadFactory) {
        };
        init();
    }

    public static PeerAwareInstanceRegistry getInstance() {
        return instance;
    }

    /**
     * Set up replica nodes and the task that updates the threshold
     * periodically.
     */
    private void init() {
        setupPeerEurekaNodes();
        scheduleRenewalThresholdUpdateTask();
    }

    /**
     * Schedule the task that updates <em>renewal threshold</em> periodically.
     * The renewal threshold would be used to determine if the renewals drop
     * dramatically because of network partition and to protect expiring too
     * many instances at a time.
     * 
     */
    private void scheduleRenewalThresholdUpdateTask() {
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                updateRenewalThreshold();

            }

        }, eurekaServerConfig.getRenewalThresholdUpdateIntervalMs(),
        eurekaServerConfig.getRenewalThresholdUpdateIntervalMs());
    }

    /**
     * Set up a schedule task to update peer eureka node information
     * periodically to determine if a node has been removed or added to the
     * list.
     */
    private void setupPeerEurekaNodes() {
        try {
            updatePeerEurekaNodes();
            timerReplicaNodes.schedule(new TimerTask() {

                @Override
                public void run() {
                    try {
                        updatePeerEurekaNodes();
                    } catch (Throwable e) {
                        logger.error("Cannot update the replica Nodes", e);
                    }

                }
            }, eurekaServerConfig.getPeerEurekaNodesUpdateIntervalMs(),
            eurekaServerConfig.getPeerEurekaNodesUpdateIntervalMs());

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Update information about peer eureka nodes.
     */
    private void updatePeerEurekaNodes() {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        List<String> replicaUrls = DiscoveryManager.getInstance()
        .getDiscoveryClient()
        .getDiscoveryServiceUrls(DiscoveryClient.getZone(myInfo));
        List<PeerEurekaNode> replicaNodes = new ArrayList<PeerEurekaNode>();
        for (String replicaUrl : replicaUrls) {
            if (!isThisMe(replicaUrl)) {
                logger.info("Adding replica node: " + replicaUrl);
                replicaNodes.add(new PeerEurekaNode(replicaUrl));
            }
        }
        if (replicaNodes.isEmpty()) {
            logger.warn("The replica size seems to be empty. Check the route 53 DNS Registry");
            return;
        }
        if (!replicaNodes.equals(peerEurekaNodes.get())) {
            List<String> previousServiceUrls = new ArrayList<String>();
            for (PeerEurekaNode node : peerEurekaNodes.get()) {
                previousServiceUrls.add(node.getServiceUrl());
            }
            List<String> currentServiceUrls = new ArrayList<String>();
            for (PeerEurekaNode node : replicaNodes) {
                currentServiceUrls.add(node.getServiceUrl());
            }
            logger.info(
                    "Updating the replica nodes as they seem to have changed from {} to {} ",
                    previousServiceUrls, currentServiceUrls);
            peerEurekaNodes.set(replicaNodes);
        }
    }

    /**
     * Populates the registry information from a peer eureka node. This
     * operation fails over to other nodes until the list is exhausted if the
     * communication fails.
     */
    public void syncUp() {
        // Copy entire entry from neighboring DS node
        LookupService lookupService = DiscoveryManager.getInstance()
        .getLookupService();

        Applications apps = lookupService.getApplications();
        int count = 0;
        for (Application app : apps.getRegisteredApplications()) {
            for (InstanceInfo instance : app.getInstances()) {
                try {
                    register(instance, -1, true);
                    count++;
                } catch (Throwable t) {
                    logger.error("During DS init copy", t);
                }
            }
        }
        // Renewals happen every 30 seconds and for a minute it should be a
        // factor of 2.
        numberOfRenewsPerMinThreshold = (int) ((count * 2) * eurekaServerConfig
                .getRenewalPercentThreshold());
        logger.info("Got "
                + count
                + " instances from neighboring DS node.  Changing status to UP.");
        logger.info("Renew threshold is: " + numberOfRenewsPerMinThreshold);
        this.startupTime = System.currentTimeMillis();
        if (count > 0) {
            this.peerInstancesTransferEmptyOnStartup = false;
        }
        ApplicationInfoManager.getInstance().setInstanceStatus(
                InstanceStatus.UP);
        super.postInit();
    }

    /**
     * Checks to see if the registry access is allowed or the server is in a
     * situation where it does not all getting registry information. The server
     * does not return registry information for a period specified in
     * {@link EurekaServerConfig#getWaitTimeInMsWhenSyncEmpty()}, if it cannot
     * get the registry information from the peer eureka nodes at start up.
     * 
     * @return false - if the instances count from a replica transfer returned
     *         zero and if the wait time has not elapsed, o otherwise returns
     *         true
     */
    public boolean shouldAllowAccess() {
        if (this.peerInstancesTransferEmptyOnStartup) {
            if (System.currentTimeMillis() > this.startupTime
                    + eurekaServerConfig.getWaitTimeInMsWhenSyncEmpty()) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the list of peer eureka nodes which is the list to replicate
     * information to.
     * 
     * @return the list of replica nodes.
     */
    public List<PeerEurekaNode> getReplicaNodes() {
        return Collections.unmodifiableList(peerEurekaNodes.get());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.InstanceRegistry#cancel(java.lang.String,
     * java.lang.String, long, boolean)
     */
    @Override
    public boolean cancel(final String appName, final String id,
            final boolean isReplication) {
        if (super.cancel(appName, id, isReplication)) {
            replicateToPeers(Action.Cancel, appName, id, null, null,
                    isReplication);
            return true;
        }
        return false;
    }

    /**
     * Registers the information about the {@link InstanceInfo} and replicates
     * this information to all peer eureka nodes. If this is replication event
     * from other replica nodes then it is not replicated.
     * 
     * @param info
     *            the {@link InstanceInfo} to be registered and replicated.
     * @param isReplication
     *            true if this is a replication event from other replica nodes,
     *            false otherwise.
     */
    public void register(final InstanceInfo info, final boolean isReplication) {
        int leaseDuration = Lease.DEFAULT_DURATION_IN_SECS;
        if (info.getLeaseInfo() != null
                && info.getLeaseInfo().getDurationInSecs() > 0) {
            leaseDuration = info.getLeaseInfo().getDurationInSecs();
        }
        super.register(info, leaseDuration, isReplication);
        replicateToPeers(Action.Register, info.getAppName(), info.getId(),
                info, null, isReplication);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.InstanceRegistry#renew(java.lang.String,
     * java.lang.String, long, boolean)
     */
    public boolean renew(final String appName, final String id,
            final boolean isReplication) {
        if (super.renew(appName, id, isReplication)) {
            replicateToPeers(Action.Heartbeat, appName, id, null, null,
                    isReplication);
            return true;
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.InstanceRegistry#statusUpdate(java.lang.String,
     * java.lang.String, com.netflix.appinfo.InstanceInfo.InstanceStatus,
     * java.lang.String, boolean)
     */
    public boolean statusUpdate(final String appName, final String id,
            final InstanceStatus newStatus, String lastDirtyTimestamp,
            final boolean isReplication) {
        if (super.statusUpdate(appName, id, newStatus, lastDirtyTimestamp,
                isReplication)) {
            replicateToPeers(Action.StatusUpdate, appName, id, null, newStatus,
                    isReplication);
            return true;
        }
        return false;
    }

    /**
     * Replicate the <em>ASG status</em> updates to peer eureka nodes. If this
     * event is a replication from other nodes, then it is not replicated to
     * other nodes.
     * 
     * @param asgName
     *            the asg name for which the status needs to be replicated.
     * @param newStatus
     *            the {@link ASGStatus} information that needs to be replicated.
     * @param isReplication
     *            true if this is a replication event from other nodes, false
     *            otherwise.
     */
    public void statusUpdate(final String asgName, final ASGStatus newStatus,
            final boolean isReplication) {
        // If this is replicated from an other node, do not try to replicate
        // again.
        if (isReplication) {
            return;
        }
        for (final PeerEurekaNode node : peerEurekaNodes.get()) {
            String serviceUrl = node.getServiceUrl();
            if (!isPeerAlive(serviceUrl)
                    && eurekaServerConfig.shouldReplicateOnlyIfUP()) {
                logger.warn(
                        "The eureka peer node {} seems to be down and hence not replicating it there",
                        serviceUrl);
            }

            try {
                replicationExecutorPool.execute(new Runnable() {
                    public void run() {
                        replicateASGInfoToReplicaNodes(asgName, newStatus, node);
                    }

                });
            } catch (RejectedExecutionException e) {
                logger.error("ReplicaAwareInstanceRegistry: RejectedExecutionException: ASGStatusUpdate "
                        + " - " + node.getServiceUrl());
                EurekaMonitors.REJECTED_REPLICATIONS.increment();
            } catch (Throwable t) {
                logger.error("ReplicaAwareInstanceRegistry: ASGStatusUpdate", t);
                EurekaMonitors.FAILED_REPLICATIONS.increment();
            }
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.InstanceRegistry#isLeaseExpirationEnabled()
     */
    @Override
    public boolean isLeaseExpirationEnabled() {
        boolean leaseExpirationEnabled = (getNumOfRenewsInLastMin() > numberOfRenewsPerMinThreshold);
        boolean isSelfPreservationModeEnabled = isSelfPreservationModeEnabled();
        if ((!leaseExpirationEnabled)) {
            if (isSelfPreservationModeEnabled) {
                logger.error("The lease expiration has been disabled since the number of renewals per minute  "
                        + " is lower than the minimum threshold. Number of Renewals Last Minute : "
                        + getNumOfRenewsInLastMin()
                        + ". The Threshold is "
                        + eurekaServerConfig.getRenewalPercentThreshold()
                        + " of total instances : "
                        + numberOfRenewsPerMinThreshold);
            } else {
                logger.warn("The self preservation mode is disabled!. Hence allowing the instances to expire.");
                leaseExpirationEnabled = true;
            }
        }
        return leaseExpirationEnabled;
    }

    /**
     * Checks to see if the self-preservation mode is enabled.
     * 
     * <p>
     * The self-preservation mode is enabled if the expected number of renewals
     * per minute {@link #getNumOfRenewsInLastMin()} is lesser than the expected
     * threshold which is determined by {@link #getNumOfRenewsPerMinThreshold()}
     * . Eureka perceives this as a danger and stops expiring instances as this
     * is most likely because of a network event. The mode is disabled only when
     * the renewals get back to above the threshold or if the flag
     * {@link EurekaServerConfig#shouldEnableSelfPreservation()} is set to
     * false.
     * </p>
     * 
     * @return true if the self-preservation mode is enabled, false otherwise.
     */
    public boolean isSelfPreservationModeEnabled() {
        return eurekaServerConfig.shouldEnableSelfPreservation();
    }

    /**
     * Perform all cleanup and shutdown operations.
     */
    void shutdown() {
        try {
            this.replicationExecutorPool.shutdown();
            DefaultMonitorRegistry.getInstance().unregister(
                    Monitors.newObjectMonitor(this));
        } catch (Throwable t) {
            logger.error("Cannot shutdown ReplicaAwareInstanceRegistry", t);
        }
    }

    @Override
    public InstanceInfo getNextServerFromEureka(String virtualHostname,
            boolean secure) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Updates the <em>renewal threshold</em> based on the current number of
     * renewals. The threshold is a percentage as specified in
     * {@link EurekaServerConfig#getRenewalPercentThreshold()} of renewals
     * received per minute {@link #getNumOfRenewsInLastMin()}.
     */
    private void updateRenewalThreshold() {
        try {
            LookupService lookupService = DiscoveryManager.getInstance()
            .getLookupService();
            Applications apps = lookupService.getApplications();
            int count = 0;
            for (Application app : apps.getRegisteredApplications()) {
                for (InstanceInfo instance : app.getInstances()) {
                    ++count;
                }
            }
            // Update threshold only if the threshold is greater than the
            // current expected threshold.
            if ((count * 2) > (eurekaServerConfig.getRenewalPercentThreshold() * numberOfRenewsPerMinThreshold)) {
                numberOfRenewsPerMinThreshold = (int) ((count * 2) * eurekaServerConfig
                        .getRenewalPercentThreshold());
                logger.info("Updated the renewal threshold to : {}",
                        numberOfRenewsPerMinThreshold);
            }
        } catch (Throwable e) {
            logger.error("Cannot update renewal threshold", e);
        }
    }

    /**
     * Gets the list of all {@link Applications} from the registry in sorted
     * lexical order of {@link Application#getName()}.
     * 
     * @return the list of {@link Applications} in lexical order.
     */
    public List<Application> getSortedApplications() {
        List<Application> apps = new ArrayList<Application>(getApplications()
                .getRegisteredApplications());
        Collections.sort(apps, APP_COMPARATOR);
        return apps;
    }

    /**
     * Gets the number of <em>renewals</em> in the last minute.
     * 
     * @return a long value representing the number of <em>renewals</em> in the
     *         last minute.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfReplicationsInLastMin", description = "Number of total replications received in the last minute", type = com.netflix.servo.annotations.DataSourceType.GAUGE)
    public long getNumOfReplicationsInLastMin() {
        return numberOfReplicationsLastMin.getCount();
    }

    /**
     * Checks if the number of renewals is lesser than threshold.
     * 
     * @return 0 if the renewals are greater than threshold, 1 otherwise.
     */
    @com.netflix.servo.annotations.Monitor(name = "isBelowRenewThreshold", description = "0 = false, 1 = true", type = com.netflix.servo.annotations.DataSourceType.GAUGE)
    public int isBelowRenewThresold() {
        if ((getNumOfRenewsInLastMin() < numberOfRenewsPerMinThreshold)
                && ((this.startupTime > 0) && (System.currentTimeMillis() > this.startupTime
                        + (eurekaServerConfig.getWaitTimeInMsWhenSyncEmpty())))) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Gets the threshold for the renewals per minute.
     * 
     * @return the integer representing the threshold for the renewals per
     *         minute.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfRenewsPerMinThreshold", type = DataSourceType.GAUGE)
    public int getNumOfRenewsPerMinThreshold() {
        return numberOfRenewsPerMinThreshold;
    }

    /**
     * Gets the number of items in the queue pending replication to other
     * peers.This gives an indication of replication latency to peer replica
     * nodes.
     * 
     * @return the long value representing the number of items in the queue
     *         pending replication to other peers.
     */
    @com.netflix.servo.annotations.Monitor(name = "itemsInReplicationPipeline", type = DataSourceType.GAUGE)
    public long getNumOfItemsInReplicationPipeline() {
        return replicationExecutorPool.getQueue().size();
    }

    /**
     * Gets the number of active threads used for replicating to peer eureka
     * nodes. This gives an indication of the headroom available to handle
     * additional replication traffic.
     * 
     * @return the long value represeting the number of active threads used for
     *         replicating to peer eureka nodes.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfActiveThreadsInReplicationPipeline", type = DataSourceType.GAUGE)
    public long getNumOfActiveThreadsInReplicationPipeline() {
        return replicationExecutorPool.getActiveCount();
    }

    /**
     * Sets the renewal threshold to the given threshold.
     * 
     * @param newThreshold
     *            new renewal threshold to be set.
     */
    public void setNumOfRenewsPerMinThreshold(int newThreshold) {
        numberOfRenewsPerMinThreshold = newThreshold;
    }

    /**
     * Checks if the given service url contains the current host which is trying
     * to replicate. Only after the EIP binding is done the host has a chance to
     * identify itself in the list of replica nodes and needs to take itself out
     * of replication traffic.
     * 
     * @param url
     *            the service url of the replica node that the check is made.
     * @return true, if the url represents the current node which is trying to
     *         replicate, false otherwise.
     */
    private boolean isThisMe(String url) {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        try {
            URI uri = new URI(url);
            return (uri.getHost().equals(myInfo.getHostName()));
        } catch (URISyntaxException e) {
            logger.error("Error in syntax", e);
            return false;
        }
    }

    /**
     * Replicates all eureka actions to peer eureka nodes except for replication
     * traffic to this node.
     * 
     */
    private void replicateToPeers(final Action action, final String appName,
            final String id, final InstanceInfo info /* optional */,
            final InstanceStatus newStatus /* optional */,
            final boolean isReplication) {
        Stopwatch tracer = action.getTimer().start();
        try {

            if (isReplication) {
                numberOfReplicationsLastMin.increment();
            }
            // If it is a replication already, do not replicate again as this
            // will create a poison replication
            if (peerEurekaNodes == Collections.EMPTY_LIST || isReplication) {
                return;
            }

            for (final PeerEurekaNode node : peerEurekaNodes.get()) {
                try {
                    replicationExecutorPool.execute(new Runnable() {
                        public void run() {
                            int retryCounter = eurekaServerConfig
                            .getNumberOfReplicationRetries();
                            // If the url represents this host, do not replicate
                            // to yourself.
                            if (isThisMe(node.getServiceUrl())) {
                                return;
                            }
                            replicateInstanceActionsToPeers(action, appName,
                                    id, info, newStatus, node, retryCounter);
                        }

                    });
                } catch (RejectedExecutionException e) {
                    logger.error("ReplicaAwareInstanceRegistry: RejectedExecutionException: "
                            + action + " - " + node.getServiceUrl());
                    EurekaMonitors.REJECTED_REPLICATIONS.increment();
                } catch (Throwable t) {
                    logger.error("ReplicaAwareInstanceRegistry: " + action, t);
                    EurekaMonitors.FAILED_REPLICATIONS.increment();
                }
            }
        } finally {
            tracer.stop();
        }
    }

    /**
     * Replicates all instance changes to peer eureka nodes except for
     * replication traffic to this node.
     * 
     */
    private void replicateInstanceActionsToPeers(final Action action,
            final String appName, final String id, final InstanceInfo info,
            final InstanceStatus newStatus, final PeerEurekaNode node,
            int retryCounter) {
        String serviceUrl = node.getServiceUrl();
        try {
            // Do not replicate if the peer is not alive and if the flag is set

            if (!isPeerAlive(serviceUrl)
                    && eurekaServerConfig.shouldReplicateOnlyIfUP()
                    // Do replicate if this is the information about Eureka
                    // itself.
                    && !(ApplicationInfoManager.getInstance().getInfo()
                            .getAppName().equals(appName))) {
                // Do not retry
                retryCounter = 0;
                logger.warn(
                        "The peer eureka node {} seems to be down and hence not replicating it there",
                        serviceUrl);
                // Clear the queue so that back log does not build up.
                node.disableStatusReplication();
                return;
            } else {
                node.enableStatusReplication();
            }
            CurrentRequestVersion.set(Version.V2);
            switch (action) {
            case Cancel:
                node.cancel(appName, id);
                break;
            case Heartbeat:
                InstanceStatus overriddenStatus = overriddenInstanceStatusMap
                .get(id);
                InstanceInfo infoFromRegistry = getInstanceByAppAndId(appName,
                        id);
                if (!node.heartbeat(appName, id, infoFromRegistry,
                        overriddenStatus)) {
                    logger.warn(
                            "Cannot find instance id {} and hence replicating the instance with status {}",
                            infoFromRegistry.getId(), infoFromRegistry
                            .getStatus().toString());
                    if (infoFromRegistry != null) {
                        node.register(infoFromRegistry);
                    }
                }
                break;
            case Register:
                node.register(info);
                break;
            case StatusUpdate:
                infoFromRegistry = getInstanceByAppAndId(appName, id);
                node.statusUpdate(appName, id, newStatus, infoFromRegistry);
                break;
            }
        } catch (Throwable t) {
            Object[] args = { action.name(), serviceUrl, id, retryCounter-- };
            logger.warn(
                    "ReplicaAwareInstanceRegistry: Failed replicating action {} for the server {} and instance id {}. Counting down from retry attempt {} ",
                    args);
            EurekaMonitors.FAILED_REPLICATIONS.increment();
            if (retryCounter > 0) {
                try {
                    Thread.sleep(REPL_RETRY_SLEEP_TIME_IN_MS);
                } catch (InterruptedException ignore) {
                }
                replicateInstanceActionsToPeers(action, appName, id, info,
                        newStatus, node, retryCounter);
            } else {
                failedReplicationAfterRetry = Monitors
                .newCounter(DICOVERY_FAILED_REPLICATION_AFTER_RETRY);
                failedReplicationAfterRetry.increment();
                Object[] args_1 = { action.name(), serviceUrl, id };
                logger.error(
                        "ReplicaAwareInstanceRegistry: Failed replicating action {} for the server {} and instance id {}. No more retries left.",
                        args_1);
                logger.error("Replication failed :", t);
            }
        }
    }

    /**
     * Replicates all ASG status changes to peer eureka nodes except for
     * replication traffic to this node.
     * 
     */
    private void replicateASGInfoToReplicaNodes(final String asgName,
            final ASGStatus newStatus, final PeerEurekaNode node) {
        boolean success = false;
        int retryCounter = eurekaServerConfig.getNumberOfReplicationRetries();
        int ctr = 0;
        CurrentRequestVersion.set(Version.V2);
        while ((!success) && (ctr++ < retryCounter)) {
            try {
                if (node.statusUpdate(asgName, newStatus)) {
                    success = true;
                } else {
                    Thread.sleep(REPL_RETRY_SLEEP_TIME_IN_MS);
                }
            } catch (Throwable e) {
                logger.error("ReplicaAwareInstanceRegistry: ASGStatusUpdate", e);
                EurekaMonitors.FAILED_REPLICATIONS.increment();
                try {
                    Thread.sleep(REPL_RETRY_SLEEP_TIME_IN_MS);
                } catch (InterruptedException e1) {
                }

            }

        }
    }

    /**
     * Check if a peer eureka instance is alive.
     * 
     * @param serviceUrl
     *            - The service url of the peer eureka node.
     * @return - true if alive, false otherwise.
     */
    private boolean isPeerAlive(String serviceUrl) {
        Stopwatch t = Monitors.newTimer("Eureka-checkReplicaAlive").start();
        boolean isReplicaAlive = peerEurekaStatusMap.get(serviceUrl);
        try {
            if (!isReplicaAlive) {
                return isPeerAliveInMyRegistery(serviceUrl);
            }
            return isReplicaAlive;
        } catch (Throwable e) {
            return true;
        } finally {
            t.stop();
        }
    }

    /**
     * Checks if the peer that the action is replicated to is alive in the local
     * registry.
     * 
     * @param serviceUrl
     *            the <em>service URL </em> of the peer.
     * @return true if it is alive, false otherwise.
     * @throws URISyntaxException
     */
    private static boolean isPeerAliveInMyRegistery(String serviceUrl)
    throws URISyntaxException {
        String myName = ApplicationInfoManager.getInstance().getInfo()
        .getAppName();
        URI uri = new URI(serviceUrl);
        Application app = PeerAwareInstanceRegistry.getInstance()
        .getApplication(myName);
        List<InstanceInfo> instanceInfoList = app.getInstances();
        for (InstanceInfo instanceInfo : instanceInfoList) {
            if (((AmazonInfo) instanceInfo.getDataCenterInfo()).get(
                    MetaDataKey.publicHostname).equalsIgnoreCase(uri.getHost())
                    && (InstanceStatus.UP.equals(instanceInfo.getStatus()))) {
                return true;
            }
        }
        return false;
    }

}
