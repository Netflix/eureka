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

package com.netflix.eureka.cluster;

import java.net.MalformedURLException;
import java.net.URL;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.EurekaHttpClient.HttpResponse;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>PeerEurekaNode</code> represents a peer node to which information
 * should be shared from this node.
 *
 * <p>
 * This class handles replicating all update operations like
 * <em>Register,Renew,Cancel,Expiration and Status Changes</em> to the eureka
 * node it represents.
 * <p>
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 */
public class PeerEurekaNode {

    private static final int RETRY_SLEEP_TIME_MS = 100;
    private static final long SERVER_UNAVAILABLE_SLEEP_TIME_MS = 1000;
    private static final long MAX_PROCESSING_DELAY_MS = 500;

    private static final Logger logger = LoggerFactory.getLogger(PeerEurekaNode.class);

    public static final String BATCH_URL_PATH = "peerreplication/batch/";

    public static final String HEADER_REPLICATION = "x-netflix-discovery-replication";

    private final String serviceUrl;
    private final EurekaServerConfig config;
    private final PeerAwareInstanceRegistry registry;
    private final String name;
    private final HttpReplicationClient replicationClient;

    private final ReplicationTaskProcessor heartBeatProcessor;
    private final ReplicationTaskProcessor statusProcessor;
    private final ReplicationTaskProcessor registerProcessor;
    private final ReplicationTaskProcessor cancelProcessor;
    private final ReplicationTaskProcessor asgStatusProcessor;

    public PeerEurekaNode(PeerAwareInstanceRegistry registry, String name, String serviceUrl, HttpReplicationClient replicationClient, EurekaServerConfig config) {
        this(registry, name, serviceUrl, replicationClient, config, MAX_PROCESSING_DELAY_MS, RETRY_SLEEP_TIME_MS, SERVER_UNAVAILABLE_SLEEP_TIME_MS);
    }

    /* For testing */ PeerEurekaNode(PeerAwareInstanceRegistry registry, String name, String serviceUrl, HttpReplicationClient replicationClient,
                                     EurekaServerConfig config,
                                     long maxProcessingDelayMs, long retrySleepTimeMs, long serverUnavailableSleepTimeMs) {
        this.registry = registry;
        this.name = name;
        this.replicationClient = replicationClient;

        this.serviceUrl = serviceUrl;
        this.config = config;

        String batcherName = getBatcherName();
        this.heartBeatProcessor = new ReplicationTaskProcessor(name, batcherName, Action.Heartbeat.name(), replicationClient, config, maxProcessingDelayMs, retrySleepTimeMs, serverUnavailableSleepTimeMs);
        this.statusProcessor = new ReplicationTaskProcessor(name, batcherName, Action.StatusUpdate.name(), replicationClient, config, maxProcessingDelayMs, retrySleepTimeMs, serverUnavailableSleepTimeMs);
        this.asgStatusProcessor = new ReplicationTaskProcessor(name, batcherName, "ASG_" + Action.StatusUpdate.name(), replicationClient, config, maxProcessingDelayMs, retrySleepTimeMs, serverUnavailableSleepTimeMs);
        this.registerProcessor = new ReplicationTaskProcessor(name, batcherName, Action.Register.name(), replicationClient, config, maxProcessingDelayMs, retrySleepTimeMs, serverUnavailableSleepTimeMs);
        this.cancelProcessor = new ReplicationTaskProcessor(name, batcherName, Action.Cancel.name(), replicationClient, config, maxProcessingDelayMs, retrySleepTimeMs, serverUnavailableSleepTimeMs);
    }

    /**
     * Sends the registration information of {@link InstanceInfo} receiving by
     * this node to the peer node represented by this class.
     *
     * @param info
     *            the instance information {@link InstanceInfo} of any instance
     *            that is send to this instance.
     * @throws Exception
     */
    public void register(final InstanceInfo info) throws Exception {
        registerProcessor.process(new InstanceReplicationTask(name, Action.Register, info, null, true) {
            public HttpResponse<Void> execute() {
                return replicationClient.register(info);
            }
        });
    }

    /**
     * Send the cancellation information of an instance to the node represented
     * by this class.
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @throws Exception
     */
    public void cancel(final String appName, final String id) throws Exception {
        cancelProcessor.process(new InstanceReplicationTask(name, Action.Cancel, appName, id) {
            @Override
            public HttpResponse<Void> execute() {
                return replicationClient.cancel(appName, id);
            }

            @Override
            public void handleFailure(int statusCode, Object responseEntity) throws Throwable {
                super.handleFailure(statusCode, responseEntity);
                if (statusCode == 404) {
                    logger.warn("{}: missing entry.", getTaskName());
                }
            }
        });
    }

    /**
     * Send the heartbeat information of an instance to the node represented by
     * this class. If the instance does not exist the node, the instance
     * registration information is sent again to the peer node.
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @param info
     *            the instance info {@link InstanceInfo} of the instance.
     * @param overriddenStatus
     *            the overridden status information if any of the instance.
     * @throws Throwable
     */
    public void heartbeat(final String appName, final String id,
                          final InstanceInfo info, final InstanceStatus overriddenStatus,
                          boolean primeConnection) throws Throwable {
        if (primeConnection) {
            // We do not care about the result for priming request.
            replicationClient.sendHeartBeat(appName, id, info, overriddenStatus);
            return;
        }
        ReplicationTask replicationTask = new InstanceReplicationTask(name, Action.Heartbeat, info, overriddenStatus, false) {
            @Override
            public HttpResponse<InstanceInfo> execute() throws Throwable {
                return replicationClient.sendHeartBeat(appName, id, info, overriddenStatus);
            }

            @Override
            public void handleFailure(int statusCode, Object responseEntity) throws Throwable {
                super.handleFailure(statusCode, responseEntity);
                if (statusCode == 404) {
                    logger.warn("{}: missing entry.", getTaskName());
                    if (info != null) {
                        logger.warn("{}: cannot find instance id {} and hence replicating the instance with status {}",
                                getTaskName(), info.getId(), info.getStatus());
                        register(info);
                    }
                } else if (config.shouldSyncWhenTimestampDiffers()) {
                    InstanceInfo peerInstanceInfo = (InstanceInfo) responseEntity;
                    if (peerInstanceInfo != null) {
                        syncInstancesIfTimestampDiffers(appName, id, info, peerInstanceInfo);
                    }
                }
            }
        };
        heartBeatProcessor.process(replicationTask);
    }

    /**
     * Send the status information of of the ASG represented by the instance.
     *
     * <p>
     * ASG (Autoscaling group) names are available for instances in AWS and the
     * ASG information is used for determining if the instance should be
     * registered as {@link InstanceStatus#DOWN} or {@link InstanceStatus#UP}.
     *
     * @param asgName
     *            the asg name if any of this instance.
     * @param newStatus
     *            the new status of the ASG.
     */
    public void statusUpdate(final String asgName, final ASGStatus newStatus) {
        asgStatusProcessor.process(new AsgReplicationTask(name, Action.StatusUpdate, asgName, newStatus) {
            public HttpResponse<?> execute() {
                return replicationClient.statusUpdate(asgName, newStatus);
            }
        });
    }

    /**
     *
     * Send the status update of the instance.
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @param newStatus
     *            the new status of the instance.
     * @param info
     *            the instance information of the instance.
     */
    public void statusUpdate(final String appName, final String id,
                             final InstanceStatus newStatus, final InstanceInfo info) {
        statusProcessor.process(new InstanceReplicationTask(name, Action.StatusUpdate, info, null, false) {
            @Override
            public HttpResponse<Void> execute() {
                return replicationClient.statusUpdate(appName, id, newStatus, info);
            }
        });
    }

    /**
     * Delete instance status override.
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @param info
     *            the instance information of the instance.
     */
    public void deleteStatusOverride(final String appName, final String id, final InstanceInfo info) {
        statusProcessor.process(new InstanceReplicationTask(name, Action.DeleteStatusOverride, info, null, false) {
            @Override
            public HttpResponse<Void> execute() {
                return replicationClient.deleteStatusOverride(appName, id, info);
            }
        });
    }

    /**
     * Get the service Url of the peer eureka node.
     *
     * @return the service Url of the peer eureka node.
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((serviceUrl == null) ? 0 : serviceUrl.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PeerEurekaNode other = (PeerEurekaNode) obj;
        if (serviceUrl == null) {
            if (other.serviceUrl != null) {
                return false;
            }
        } else if (!serviceUrl.equals(other.serviceUrl)) {
            return false;
        }
        return true;
    }

    /**
     * Shuts down all resources used for peer replication.
     */
    public void shutDown() {
        heartBeatProcessor.shutdown();
        registerProcessor.shutdown();
        cancelProcessor.shutdown();
        statusProcessor.shutdown();
        asgStatusProcessor.shutdown();
        replicationClient.shutdown();
    }

    /**
     * Synchronize {@link InstanceInfo} information if the timestamp between
     * this node and the peer eureka nodes vary.
     */
    private void syncInstancesIfTimestampDiffers(String appName, String id, InstanceInfo info, InstanceInfo infoFromPeer) {
        try {
            if (infoFromPeer != null) {
                logger.warn("Peer wants us to take the instance information from it, since the timestamp differs,"
                        + "Id : {} My Timestamp : {}, Peer's timestamp: {}", id, info.getLastDirtyTimestamp(), infoFromPeer.getLastDirtyTimestamp());

                if (infoFromPeer.getOverriddenStatus() != null && !InstanceStatus.UNKNOWN.equals(infoFromPeer.getOverriddenStatus())) {
                    logger.warn("Overridden Status info -id {}, mine {}, peer's {}", id, info.getOverriddenStatus(), infoFromPeer.getOverriddenStatus());
                    registry.storeOverriddenStatusIfRequired(appName, id, infoFromPeer.getOverriddenStatus());
                }
                registry.register(infoFromPeer, true);
            }
        } catch (Throwable e) {
            logger.warn("Exception when trying to set information from peer :", e);
        }
    }

    public String getBatcherName() {
        String batcherName;
        try {
            batcherName = new URL(serviceUrl).getHost();
        } catch (MalformedURLException e1) {
            batcherName = serviceUrl;
        }
        return batcherName;
    }
}
