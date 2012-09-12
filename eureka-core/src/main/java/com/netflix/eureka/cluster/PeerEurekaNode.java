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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.EurekaJerseyClient;
import com.netflix.discovery.shared.EurekaJerseyClient.JerseyClient;
import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.Version;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache4.ApacheHttpClient4;

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

    private static final Logger logger = LoggerFactory
    .getLogger(PeerEurekaNode.class);

    private static final int RETRY_SLEEP_TIME_MS = 100;

    public static final String HEADER_REPLICATION = "x-netflix-discovery-replication";
    private static final EurekaServerConfig config = EurekaServerConfigurationManager
    .getInstance().getConfiguration();
    private final Timer registerTimer = Monitors.newTimer("Register");
    private final Timer cancelTimer = Monitors.newTimer("Cancel");
    private final Timer renewTimer = Monitors.newTimer("Renew");
    private final Timer asgStatusUpdateTimer = Monitors
    .newTimer("ASGStatusUpdate");
    private final Timer instanceStatusUpdateTimer = Monitors
    .newTimer("InstanceStatusUpdate");
    private final String serviceUrl;
    private final String name;
    private JerseyClient jerseyClient;
    private ApacheHttpClient4 jerseyApacheClient;
    private ThreadPoolExecutor statusReplicationPool;
    private volatile boolean statusReplication = true;

    public PeerEurekaNode(String serviceUrl) {
        this.serviceUrl = serviceUrl.intern();
        this.name = getClass().getSimpleName() + ": " + serviceUrl + "apps/: ";

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(false)
        .setNameFormat("Eureka-ReplicaNode-Thread-" + serviceUrl)
        .build();

        statusReplicationPool = new ThreadPoolExecutor(
                config.getMinThreadsForStatusReplication(),
                config.getMaxThreadsForStatusReplication(),
                config.getMaxIdleThreadInMinutesAgeForStatusReplication(),
                TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(
                        config.getMaxElementsInStatusReplicationPool()),
                        threadFactory) {
        };

        synchronized (this.serviceUrl) {

            if (jerseyApacheClient == null) {
                try {
                    jerseyClient = EurekaJerseyClient.createJerseyClient(
                            config.getPeerNodeConnectTimeoutMs(),
                            config.getPeerNodeReadTimeoutMs(),
                            config.getPeerNodeTotalConnections(),
                            config.getPeerNodeTotalConnectionsPerHost(),
                            config.getPeerNodeConnectionIdleTimeoutSeconds());
                    jerseyApacheClient = jerseyClient.getClient();
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Cannot Create new Replica Node :" + name);
                }
            }
        }
        try {

            Monitors.registerObject(serviceUrl, this);

        } catch (Throwable e) {
            logger.warn(
                    "Cannot register the JMX monitor for the InstanceRegistry :",
                    e);
        }

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
    public void register(InstanceInfo info) throws Exception {
        Stopwatch tracer = registerTimer.start();
        String urlPath = "apps/" + info.getAppName();
        ClientResponse response = null;

        try {
            response = jerseyApacheClient.resource(serviceUrl).path(urlPath)
            .header(HEADER_REPLICATION, "true")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .post(ClientResponse.class, info);

        } finally {
            if (response != null) {
                response.close();
            }
            if (tracer != null) {
                tracer.stop();
            }
        }
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
    public void cancel(String appName, String id) throws Exception {
        ClientResponse response = null;
        Stopwatch tracer = cancelTimer.start();

        try {
            String urlPath = "apps/" + appName + "/" + id;
            response = jerseyApacheClient.resource(serviceUrl).path(urlPath)
            .header(HEADER_REPLICATION, "true")
            .delete(ClientResponse.class);
            if (response.getStatus() == 404) {
                logger.warn(name + appName + "/" + id
                        + " : delete: missing entry.");
            }

        } finally {
            if (response != null) {
                response.close();
            }
            if (tracer != null) {
                tracer.stop();
            }
        }
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
     * @return true, if the instance exists in the peer node, false otherwise.
     * @throws Exception
     */
    public boolean heartbeat(String appName, String id, InstanceInfo info,
            InstanceStatus overriddenStatus) throws Exception {
        ClientResponse response = null;
        Stopwatch tracer = renewTimer.start();
        try {
            String urlPath = "apps/" + appName + "/" + id;
            WebResource r = jerseyApacheClient
            .resource(serviceUrl)
            .path(urlPath)
            .queryParam("status", info.getStatus().toString())
            .queryParam("lastDirtyTimestamp",
                    info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                r = r.queryParam("overriddenstatus", overriddenStatus.name());
            }
            response = r.header(HEADER_REPLICATION, "true").put(
                    ClientResponse.class);

            if (response.getStatus() == 404) {
                logger.warn(name + appName + "/" + id
                        + " : heartbeat: missing entry.");
                return false;
            } else if (response.getStatus() == Status.OK.getStatusCode()) {
                syncInstancesIfTimestampDiffers(id, info, response);
            }
        } finally {
            if (response != null) {
                response.close();
            }
            if (tracer != null) {
                tracer.stop();
            }
        }
        return true;
    }

    /**
     * Synchronize {@link InstanceInfo} information if the timestamp between
     * this node and the peer eureka nodes vary.
     */
    private void syncInstancesIfTimestampDiffers(String id, InstanceInfo info,
            ClientResponse response) {
        try {
            if (config.shouldSyncWhenTimestampDiffers() && response.hasEntity()) {
                InstanceInfo infoFromPeer = response
                        .getEntity(InstanceInfo.class);
                if (infoFromPeer != null) {
                    Object[] args = { id, info.getLastDirtyTimestamp(),
                            infoFromPeer.getLastDirtyTimestamp() };

                    logger.warn(
                            "Peer wants us to take the instance information from it, since the timestamp differs,Id : {} My Timestamp : {}, Peer's timestamp: {}",
                            args);
                    if ((infoFromPeer.getOverriddenStatus() != null)
                            && !(InstanceStatus.UNKNOWN.equals(infoFromPeer
                                    .getOverriddenStatus()))) {
                        Object[] args1 = { id, info.getOverriddenStatus(),
                                infoFromPeer.getOverriddenStatus() };
                        logger.warn(
                                "Overridden Status info -id {}, mine {}, peer's {}",
                                args1);

                        PeerAwareInstanceRegistry.getInstance()
                                .storeOverriddenStatusIfRequired(id,
                                        infoFromPeer.getOverriddenStatus());
                    }
                    PeerAwareInstanceRegistry.getInstance().register(
                            infoFromPeer, true);

                }

            }
        } catch (Throwable e) {
            logger.warn("Exception when trying to get information from peer :",
                    e);
        }
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
     * @return true if the status update succeeds, false otherwise.
     * @throws Exception
     */
    public boolean statusUpdate(String asgName, ASGStatus newStatus)
    throws Exception {
        ClientResponse response = null;
        Stopwatch tracer = asgStatusUpdateTimer.start();
        try {
            String urlPath = "asg/" + asgName + "/status";
            response = jerseyApacheClient.resource(serviceUrl).path(urlPath)
            .queryParam("value", newStatus.name())
            .header(HEADER_REPLICATION, "true")
            .put(ClientResponse.class);

            if (response.getStatus() != 200) {
                logger.error(name + asgName + " : statusUpdate:  failed!");
            } else {
                return true;
            }
            tracer.stop();
            return false;
        } finally {
            if (response != null) {
                response.close();
            }
            if (tracer != null) {
                tracer.stop();
            }
        }
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
     * @return true if the udpate succeeded, false otherwise.
     * @throws Throwable
     */
    public boolean statusUpdate(final String appName, final String id,
            final InstanceStatus newStatus, final InstanceInfo info)
    throws Throwable {
        statusReplicationPool.execute(new Runnable() {

            @Override
            public void run() {
                CurrentRequestVersion.set(Version.V2);
                boolean success = false;
                while (!success) {
                    ClientResponse response = null;
                    Stopwatch tracer = instanceStatusUpdateTimer.start();
                    try {
                        String urlPath = "apps/" + appName + "/" + id
                        + "/status";
                        response = jerseyApacheClient
                        .resource(serviceUrl)
                        .path(urlPath)
                        .queryParam("value", newStatus.name())
                        .queryParam("lastDirtyTimestamp",
                                info.getLastDirtyTimestamp().toString())
                                .header(HEADER_REPLICATION, "true")
                                .put(ClientResponse.class);
                        if (response.getStatus() != 200) {
                            logger.error(name + appName + "/" + id
                                    + " : statusUpdate:  failed!");
                        }
                        success = true;
                    } catch (Throwable e) {
                        logger.error(name + appName + "/" + id
                                + " : statusUpdate:  failed!", e);
                        try {
                            Thread.sleep(RETRY_SLEEP_TIME_MS);
                        } catch (InterruptedException e1) {

                        }
                        if ((!config.shouldRetryIndefinitelyToReplicateStatus())
                                || (!statusReplication)) {
                            success = true;
                        }

                    } finally {
                        if (response != null) {
                            response.close();
                        }
                        if (tracer != null) {
                            tracer.stop();
                        }
                    }

                }

            }

        });
        return true;
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
        result = prime * result
        + ((serviceUrl == null) ? 0 : serviceUrl.hashCode());
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
        PeerEurekaNode other = (PeerEurekaNode) obj;
        if (serviceUrl == null) {
            if (other.serviceUrl != null)
                return false;
        } else if (!serviceUrl.equals(other.serviceUrl))
            return false;
        return true;
    }

    /**
     * Get the number of items in the replication pipeline yet to be replicated.
     * 
     * @return the long value representing the number of items in the
     *         replication pipeline yet to be replicated..
     */
    @com.netflix.servo.annotations.Monitor(name = "itemsInReplicationPipeline", type = DataSourceType.GAUGE)
    public long getNumOfItemsInReplicationPipeline() {
        return statusReplicationPool.getQueue().size();
    }

    /**
     * Disables the status replication and clears the internal queue.
     */
    public void disableStatusReplication() {
        if (statusReplicationPool.getQueue().size() > 0) {
            logger.info("Clearing the internal status queue for {}", serviceUrl);
            statusReplicationPool.getQueue().clear();
        }
        this.statusReplication = false;
    }

    /**
     * Enable the status replication.
     */
    public void enableStatusReplication() {
        this.statusReplication = true;
    }

}
