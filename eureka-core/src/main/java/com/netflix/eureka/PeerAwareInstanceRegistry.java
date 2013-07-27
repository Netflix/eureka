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
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.lease.Lease;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.eureka.util.MeasuredRate;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceType;
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
    private static final String US_EAST_1 = "us-east-1";

    private static final int PRIME_PEER_NODES_RETRY_MS = 30000;

    private static final int REGISTRY_SYNC_RETRY_MS = 30000;

    private static final Logger logger = LoggerFactory
            .getLogger(PeerAwareInstanceRegistry.class);

    private static final EurekaServerConfig EUREKA_SERVER_CONFIG = EurekaServerConfigurationManager
            .getInstance().getConfiguration();
    private static final EurekaClientConfig EUREKA_CLIENT_CONFIG = DiscoveryManager
            .getInstance().getEurekaClientConfig();

    private long startupTime = 0;
    private boolean peerInstancesTransferEmptyOnStartup = true;
    private static final Timer timerReplicaNodes = new Timer(
            "Eureka-PeerNodesUpdater", true);

    public enum Action {
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

   
    private AtomicReference<List<PeerEurekaNode>> peerEurekaNodes;

    private Timer timer = new Timer(
            "ReplicaAwareInstanceRegistry - RenewalThresholdUpdater", true);

    private static final PeerAwareInstanceRegistry instance = new PeerAwareInstanceRegistry();

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

        }, EUREKA_SERVER_CONFIG.getRenewalThresholdUpdateIntervalMs(),
                EUREKA_SERVER_CONFIG.getRenewalThresholdUpdateIntervalMs());
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
            }, EUREKA_SERVER_CONFIG.getPeerEurekaNodesUpdateIntervalMs(),
                    EUREKA_SERVER_CONFIG.getPeerEurekaNodesUpdateIntervalMs());

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
        List<PeerEurekaNode> existingReplicaNodes = peerEurekaNodes.get();
        if (!replicaNodes.equals(existingReplicaNodes)) {
            List<String> previousServiceUrls = new ArrayList<String>();
            for (PeerEurekaNode node : existingReplicaNodes) {
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
            for (PeerEurekaNode existingReplicaNode : existingReplicaNodes) {
                existingReplicaNode.destroyResources();
            }
        } else {
            for (PeerEurekaNode replicaNode : replicaNodes) {
                replicaNode.destroyResources();
            }
        }
    }

    /**
     * Populates the registry information from a peer eureka node. This
     * operation fails over to other nodes until the list is exhausted if the
     * communication fails.
     */
    public int syncUp() {
        // Copy entire entry from neighboring DS node
        LookupService lookupService = DiscoveryManager.getInstance()
                .getLookupService();
        int count = 0;

        for (int i = 0; ((i < EUREKA_SERVER_CONFIG.getRegistrySyncRetries()) && (count == 0)); i++) {
            Applications apps = lookupService.getApplications();
            for (Application app : apps.getRegisteredApplications()) {
                for (InstanceInfo instance : app.getInstances()) {
                    try {
                        if (isRegisterable(instance)) {

                            register(instance, instance.getLeaseInfo()
                                    .getDurationInSecs(), true);
                            count++;
                        }
                    } catch (Throwable t) {
                        logger.error("During DS init copy", t);
                    }
                }
            }
            if (count == 0) {
                try {
                    Thread.sleep(REGISTRY_SYNC_RETRY_MS);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted during registry transfer..");
                    break;
                }
            }
        }
        return count;
    }

    public void openForTraffic(int count) {
        // Renewals happen every 30 seconds and for a minute it should be a
        // factor of 2.
        this.expectedNumberOfRenewsPerMin = count * 2;
        this.numberOfRenewsPerMinThreshold = (int)(this.expectedNumberOfRenewsPerMin * EUREKA_SERVER_CONFIG
                .getRenewalPercentThreshold());
        logger.info("Got " + count + " instances from neighboring DS node");
        logger.info("Renew threshold is: " + numberOfRenewsPerMinThreshold);
        this.startupTime = System.currentTimeMillis();
        if (count > 0) {
            this.peerInstancesTransferEmptyOnStartup = false;
        }
        boolean isAws = (Name.Amazon.equals(ApplicationInfoManager
                .getInstance().getInfo().getDataCenterInfo().getName()));
        if (isAws && EUREKA_SERVER_CONFIG.shouldPrimeAwsReplicaConnections()) {
            logger.info("Priming AWS connections for all replicas..");
            primeAwsReplicas();
        }
        logger.info("Changing status to UP");
        ApplicationInfoManager.getInstance().setInstanceStatus(
                InstanceStatus.UP);
        super.postInit();
    }

    /**
     * Prime connections for Aws replicas.
     * <p>
     * Sometimes when the eureka servers comes up, AWS firewall may not allow
     * the network connections immediately. This will cause the outbound
     * connections to fail, but the inbound connections continue to work. What
     * this means is the clients would have switched to this node (after EIP
     * binding) and so the other eureka nodes will expire all instances that
     * have been switched because of the lack of outgoing heartbeats from this
     * instance.
     * </p>
     * <p>
     * The best protection in this scenario is to block and wait until we are
     * able to ping all eureka nodes successfully atleast once. Until then we
     * won't open up the traffic.
     * </p>
     */
    private void primeAwsReplicas() {
        boolean areAllPeerNodesPrimed = false;
        while (!areAllPeerNodesPrimed) {
            String peerHostName = null;
            try {
                Application eurekaApps = this.getApplication(
                        ApplicationInfoManager.getInstance().getInfo()
                                .getAppName(), false);
                if (eurekaApps == null) {
                    areAllPeerNodesPrimed = true;
                }
                for (PeerEurekaNode node : peerEurekaNodes.get()) {
                    for (InstanceInfo peerInstanceInfo : eurekaApps
                            .getInstances()) {
                        LeaseInfo leaseInfo = peerInstanceInfo.getLeaseInfo();
                        // If the lease is expired - do not worry about priming
                        if (System.currentTimeMillis() > (leaseInfo
                                .getRenewalTimestamp() + (leaseInfo
                                .getDurationInSecs() * 1000))
                                + (2 * 60 * 1000)) {
                            continue;
                        }
                        peerHostName = peerInstanceInfo.getHostName();
                        logger.info(
                                "Trying to send heartbeat for the eureka server at {} to make sure the network channels are open",
                                peerHostName);
                        // Only try to contact the eureka nodes that are in this
                        // instance's registry - because
                        // the other instances may be legitimately down
                        if (peerHostName.equalsIgnoreCase(new URI(node
                                .getServiceUrl()).getHost())) {
                            node.heartbeat(peerInstanceInfo.getAppName(),
                                    peerInstanceInfo.getId(), peerInstanceInfo,
                                    null, true);
                        }
                    }
                }
                areAllPeerNodesPrimed = true;
            } catch (Throwable e) {
                logger.error("Could not contact " + peerHostName, e);
                try {
                    Thread.sleep(PRIME_PEER_NODES_RETRY_MS);
                } catch (InterruptedException e1) {
                    logger.warn("Interrupted while priming : ", e1);
                    areAllPeerNodesPrimed = true;
                }
            }
        }
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
            if (!(System.currentTimeMillis() > this.startupTime
                    + EUREKA_SERVER_CONFIG.getWaitTimeInMsWhenSyncEmpty())) {
                return false;
            }
        }
        for (RemoteRegionRegistry remoteRegionRegistry : this.regionNameVSRemoteRegistry.values()) {
            if (!remoteRegionRegistry.isReadyForServingData()) {
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
            synchronized (lock) {
                if (this.expectedNumberOfRenewsPerMin > 0) {
                    // Since the client wants to cancel it, reduce the threshold
                    // (1
                    // for 30 seconds, 2 for a minute)
                    this.expectedNumberOfRenewsPerMin = this.expectedNumberOfRenewsPerMin - 2;
                    this.numberOfRenewsPerMinThreshold = (int) (this.expectedNumberOfRenewsPerMin * EUREKA_SERVER_CONFIG
                            .getRenewalPercentThreshold());
                }
            }
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
            replicateASGInfoToReplicaNodes(asgName, newStatus, node);

        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.InstanceRegistry#isLeaseExpirationEnabled()
     */
    @Override
    public boolean isLeaseExpirationEnabled() {
        boolean leaseExpirationEnabled = (numberOfRenewsPerMinThreshold > 0)
                && (getNumOfRenewsInLastMin() > numberOfRenewsPerMinThreshold);
        boolean isSelfPreservationModeEnabled = isSelfPreservationModeEnabled();
        if ((!leaseExpirationEnabled)) {
            if (isSelfPreservationModeEnabled) {
                logger.error("The lease expiration has been disabled since the number of renewals per minute  "
                        + " is lower than the minimum threshold. Number of Renewals Last Minute : "
                        + getNumOfRenewsInLastMin()
                        + ". The Threshold is "
                        + EUREKA_SERVER_CONFIG.getRenewalPercentThreshold()
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
        return EUREKA_SERVER_CONFIG.shouldEnableSelfPreservation();
    }

    /**
     * Perform all cleanup and shutdown operations.
     */
    void shutdown() {
        try {
            DefaultMonitorRegistry.getInstance().unregister(
                    Monitors.newObjectMonitor(this));
        } catch (Throwable t) {
            logger.error("Cannot shutdown monitor registry", t);
        }
        try {
            for (PeerEurekaNode node : this.peerEurekaNodes.get()) {
                node.shutDown();
            }
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
                    if (this.isRegisterable(instance)) {
                        ++count;
                    }
                }
            }
            synchronized (lock) {
                // Update threshold only if the threshold is greater than the
                // current expected threshold of if the self preservation is disabled.
               if ((count * 2) > (EUREKA_SERVER_CONFIG
                        .getRenewalPercentThreshold() * numberOfRenewsPerMinThreshold)
                        || (!this.isSelfPreservationModeEnabled())) {
                    this.expectedNumberOfRenewsPerMin = count * 2;
                    this.numberOfRenewsPerMinThreshold = (int) ((count * 2) * EUREKA_SERVER_CONFIG
                            .getRenewalPercentThreshold());
                }
            }
            logger.info("Current renewal threshold is : {}",
                    numberOfRenewsPerMinThreshold);
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
                        + (EUREKA_SERVER_CONFIG.getWaitTimeInMsWhenSyncEmpty())))) {
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
     * Checks if an instance is registerable in this region. Instances from
     * other regions are rejected.
     * 
     * @param instanceInfo
     *            - the instance info information of the instance
     * @return - true, if it can be registered in this server, false otherwise.
     */
    public boolean isRegisterable(InstanceInfo instanceInfo) {
        DataCenterInfo datacenterInfo = instanceInfo.getDataCenterInfo();
        String serverRegion = EUREKA_CLIENT_CONFIG.getRegion();
        if (AmazonInfo.class.isInstance(datacenterInfo)) {
            AmazonInfo info = AmazonInfo.class.cast(instanceInfo
                    .getDataCenterInfo());
            String availabilityZone = info.get(MetaDataKey.availabilityZone);
            // Can be null for dev environments in non-AWS data center
            if (availabilityZone == null
                    && US_EAST_1.equalsIgnoreCase(serverRegion)) {
                return true;
            } else if ((availabilityZone != null)
                    && (availabilityZone.contains(serverRegion))) {
                // If in the same region as server, then consider it
                // registerable
                return true;
            }
        }
        return false;
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
    private void replicateToPeers(Action action, String appName, String id,
            InstanceInfo info /* optional */,
            InstanceStatus newStatus /* optional */, boolean isReplication) {
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
                // If the url represents this host, do not replicate
                // to yourself.
                if (isThisMe(node.getServiceUrl())) {
                    continue;
                }
                replicateInstanceActionsToPeers(action, appName, id, info,
                        newStatus, node);
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
    private void replicateInstanceActionsToPeers(Action action, String appName,
            String id, InstanceInfo info, InstanceStatus newStatus,
            PeerEurekaNode node) {
        try {
            InstanceInfo infoFromRegistry = null;
            CurrentRequestVersion.set(Version.V2);
            switch (action) {
            case Cancel:
                node.cancel(appName, id);
                break;
            case Heartbeat:
                InstanceStatus overriddenStatus = overriddenInstanceStatusMap
                        .get(id);
                infoFromRegistry = getInstanceByAppAndId(appName, id, false);
                node.heartbeat(appName, id, infoFromRegistry, overriddenStatus,
                        false);
                break;
            case Register:
                node.register(info);
                break;
            case StatusUpdate:
                infoFromRegistry = getInstanceByAppAndId(appName, id, false);
                node.statusUpdate(appName, id, newStatus, infoFromRegistry);
                break;

            }
        } catch (Throwable t) {
            logger.error(
                    "Cannot replicate information to " + node.getServiceUrl()
                            + " for action " + action.name(), t);
        }
    }

    /**
     * Replicates all ASG status changes to peer eureka nodes except for
     * replication traffic to this node.
     * 
     */
    private void replicateASGInfoToReplicaNodes(final String asgName,
            final ASGStatus newStatus, final PeerEurekaNode node) {
        CurrentRequestVersion.set(Version.V2);
        try {
            node.statusUpdate(asgName, newStatus);

        } catch (Throwable e) {
            logger.error(
                    "Cannot replicate ASG status information to "
                            + node.getServiceUrl(), e);
        }

    }

}
