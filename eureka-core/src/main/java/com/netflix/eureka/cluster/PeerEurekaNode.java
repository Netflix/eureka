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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.EurekaJerseyClient;
import com.netflix.discovery.shared.EurekaJerseyClient.JerseyClient;
import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistry.Action;
import com.netflix.eureka.Version;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.logging.messaging.BatcherFactory;
import com.netflix.logging.messaging.MessageBatcher;
import com.netflix.logging.messaging.MessageProcessor;
import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.servo.monitor.Monitors;
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

    private final String serviceUrl;
    private final String name;
    private volatile JerseyClient jerseyClient;
    private volatile ApacheHttpClient4 jerseyApacheClient;
    private MessageBatcher<ReplicationTask> heartBeatBatcher;
    private MessageBatcher<ReplicationTask> statusBatcher;
    private MessageBatcher<ReplicationTask> registerBatcher;
    private MessageBatcher<ReplicationTask> cancelBatcher;

    public PeerEurekaNode(String serviceUrl) {
        this.serviceUrl = serviceUrl.intern();
        this.name = getClass().getSimpleName() + ": " + serviceUrl + "apps/: ";
        this.heartBeatBatcher = getBatcher(serviceUrl, Action.Heartbeat);
        this.statusBatcher = getBatcher(serviceUrl, Action.StatusUpdate);
        this.registerBatcher = getBatcher(serviceUrl, Action.Register);
        this.cancelBatcher = getBatcher(serviceUrl, Action.Cancel);

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
            String serviceUrlHost = new URL(serviceUrl).getHost();
            Monitors.registerObject(serviceUrlHost, this);
        } catch (Throwable e) {
            logger.error("Cannot register monitors for Peer eureka node :"
                    + serviceUrl, e);
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
    public void register(final InstanceInfo info) throws Exception {
        boolean success = registerBatcher.process(new ReplicationTask(info
                .getAppName(), info.getId(), Action.Register) {
            public void execute() {
                CurrentRequestVersion.set(Version.V2);
                String urlPath = "apps/" + info.getAppName();
                ClientResponse response = null;
                try {
                    response = jerseyApacheClient.resource(serviceUrl)
                            .path(urlPath).header(HEADER_REPLICATION, "true")
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .post(ClientResponse.class, info);

                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
        if (!success) {
            logger.error("Cannot find space in the replication pool for "
                    + this.serviceUrl
                    + ". Check the network connectivity or the traffic");
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
    public void cancel(final String appName, final String id) throws Exception {
        boolean success = cancelBatcher.process(new ReplicationTask(appName,
                id, Action.Cancel) {
            @Override
            public void execute() {
                ClientResponse response = null;
                try {
                    String urlPath = "apps/" + appName + "/" + id;
                    response = jerseyApacheClient.resource(serviceUrl)
                            .path(urlPath).header(HEADER_REPLICATION, "true")
                            .delete(ClientResponse.class);
                    if (response.getStatus() == 404) {
                        logger.warn(name + appName + "/" + id
                                + " : delete: missing entry.");
                    }

                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
        if (!success) {
            logger.error("Cannot find space in the replication pool for "
                    + this.serviceUrl
                    + ". Check the network connectivity or the traffic");
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
     * @throws Throwable
     */
    public void heartbeat(final String appName, final String id,
            final InstanceInfo info, final InstanceStatus overriddenStatus,
            boolean primeConnection) throws Throwable {
        if (primeConnection) {
            sendHeartBeat(appName, id, info, overriddenStatus);
            return;
        }
        boolean success = heartBeatBatcher.process(new ReplicationTask(appName,
                id, Action.Heartbeat) {
            @Override
            public void execute() throws Throwable {
                sendHeartBeat(appName, id, info, overriddenStatus);
            }

        });
        if (!success) {
            logger.error("Cannot find space in the replication pool for "
                    + this.serviceUrl
                    + ". Check the network connectivity or the traffic");
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
     */
    public void statusUpdate(final String asgName, final ASGStatus newStatus) {
        boolean success = statusBatcher.process(new ReplicationTask(asgName,
                asgName, Action.StatusUpdate) {
            public void execute() {
                ClientResponse response = null;
                try {
                    String urlPath = "asg/" + asgName + "/status";
                    response = jerseyApacheClient.resource(serviceUrl)
                            .path(urlPath)
                            .queryParam("value", newStatus.name())
                            .header(HEADER_REPLICATION, "true")
                            .put(ClientResponse.class);

                    if (response.getStatus() != 200) {
                        logger.error(name + asgName
                                + " : statusUpdate:  failed!");
                    }
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }

            }
        });
        if (!success) {
            logger.error("Cannot find space in the replication pool for "
                    + this.serviceUrl
                    + ". Check the network connectivity or the traffic");
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
     * @return true if the update succeeded, false otherwise.
     */
    public void statusUpdate(final String appName, final String id,
            final InstanceStatus newStatus, final InstanceInfo info) {
        boolean success = statusBatcher.process(new ReplicationTask(appName,
                id, Action.StatusUpdate) {

            @Override
            public void execute() {
                CurrentRequestVersion.set(Version.V2);
                ClientResponse response = null;
                try {
                    String urlPath = "apps/" + appName + "/" + id + "/status";
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
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }

            }

        });
        if (!success) {
            logger.error("Cannot find space in the replication pool for "
                    + this.serviceUrl
                    + ". Check the network connectivity or the traffic");
        }
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
     * Destroy the resources created for communication with the Peer Eureka
     * Server.
     */
    public void destroyResources() {
        if (jerseyClient != null) {
            try {
                jerseyClient.destroyResources();
            } catch (Throwable ignore) {

            }
        }

    }

    /**
     * Shuts down all resources used for peer replication.
     */
    public void shutDown() {
        if (this.heartBeatBatcher != null) {
            this.heartBeatBatcher.stop();
        }

        if (this.registerBatcher != null) {
            this.registerBatcher.stop();
        }
        if (this.cancelBatcher != null) {
            this.cancelBatcher.stop();
        }
        if (this.statusBatcher != null) {
            this.statusBatcher.stop();
        }
    }

    /**
     * Sends heartbeats to peer eureka nodes.
     * 
     * @param appName
     *            - the application name for which the hearbeat needs to be
     *            sent.
     * @param id
     *            - the unique identifier of the heartbeat.
     * @param info
     *            - the {@link InstanceInfo} of the instance for which the
     *            heartbeat need to be sent.
     * @param overriddenStatus
     *            the overridden status of the instance.
     * @throws Throwable
     */
    private void sendHeartBeat(final String appName, final String id,
            final InstanceInfo info, final InstanceStatus overriddenStatus)
            throws Throwable {
        ClientResponse response = null;
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
                if (info != null) {
                    logger.warn(
                            "Cannot find instance id {} and hence replicating the instance with status {}",
                            info.getId(), info.getStatus().toString());
                    register(info);
                }
            } else if (response.getStatus() == Status.OK.getStatusCode()) {
                syncInstancesIfTimestampDiffers(id, info, response);
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Check if the exception is some sort of network timeout exception (ie)
     * read,connect.
     * 
     * @param e
     *            The exception for which the information needs to be found.
     * @return true, if it is a network timeout, false otherwise.
     */
    private boolean isNetworkConnectException(Throwable e) {
        while (e.getCause() != null) {
            if (IOException.class.isInstance(e.getCause())) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    /**
     * Class that simply tracks the time the task was created.
     */
    private abstract class ReplicationTask {
        private long submitTime = System.currentTimeMillis();
        private String appName;
        private String id;
        private Action action;

        public String getAppName() {
            return appName;
        }

        public String getId() {
            return id;
        }

        public Action getAction() {
            return action;
        }

        public long getSubmitTime() {
            return this.submitTime;
        }

        public ReplicationTask(String appName, String id, Action action) {
            this.appName = appName;
            this.id = id;
            this.action = action;
        }

        public abstract void execute() throws Throwable;
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
     * Get the batcher to process the replication events asynchronously.
     * 
     * @param serviceUrl
     *            the serviceUrl for which the events needs to be replicated
     * @param action
     *            the action that indicates the type of replication event -
     *            registrations, heartbeat etc
     * @return The batcher instance
     */
    private MessageBatcher getBatcher(String serviceUrl, Action action) {
        String batcherName = null;
        try {
            batcherName = new URL(serviceUrl).getHost();
        } catch (MalformedURLException e1) {
            batcherName = serviceUrl;
        }
        String absoluteBatcherName = batcherName + "-" + action.name();
        ConfigurationManager.getConfigInstance().setProperty(
                "batcher." + absoluteBatcherName + ".queue.maxMessages",
                config.getMaxElementsInPeerReplicationPool());
        ConfigurationManager.getConfigInstance().setProperty(
                "batcher." + absoluteBatcherName + ".keepAliveTime",
                config.getMaxIdleThreadAgeInMinutesForPeerReplication() * 60);
        ConfigurationManager.getConfigInstance().setProperty(
                "batcher." + absoluteBatcherName + ".maxThreads",
                config.getMaxThreadsForPeerReplication());

        return BatcherFactory.createBatcher(absoluteBatcherName,
                new MessageProcessor<ReplicationTask>() {

                    @Override
                    public void process(List<ReplicationTask> tasks) {
                        for (ReplicationTask task : tasks) {
                            boolean done = true;
                            do {
                                done = true;
                                try {
                                    Object[] args = {
                                            task.getAppName(),
                                            task.getId(),
                                            task.getAction(),
                                            new Date(System.currentTimeMillis()),
                                            new Date(task.getSubmitTime()) };
                                    if (System.currentTimeMillis()
                                            - config.getMaxTimeForReplication() > task
                                            .getSubmitTime()) {
                                        logger.warn(
                                                "Replication events older than the threshold. AppName : {}, Id: {}, Action : {}, Current Time : {}, Submit Time :{}",
                                                args);

                                        continue;
                                    }
                                    task.execute();
                                } catch (Throwable e) {
                                    logger.error(
                                            name + task.getAppName() + "/"
                                                    + task.getId() + ":"
                                                    + task.getAction(), e);
                                    try {
                                        Thread.sleep(RETRY_SLEEP_TIME_MS);
                                    } catch (InterruptedException e1) {

                                    }
                                    if ((isNetworkConnectException(e))) {
                                        DynamicCounter.increment(task
                                                .getAction().name()
                                                + "_retries");
                                        done = false;
                                    } else {
                                        logger.info(
                                                "Not re-trying this exception because it does not seem to be a network exception",
                                                e);
                                    }
                                }
                            } while (!done);
                        }
                    }
                });
    }

}
