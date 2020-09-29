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
package com.netflix.eureka.registry;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaIdentityHeaderFilter;
import com.netflix.discovery.TimedSupervisorTask;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.StaticClusterResolver;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerIdentity;
import com.netflix.eureka.resources.ServerCodecs;
import com.netflix.eureka.transport.EurekaServerHttpClients;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.eureka.Names.METRIC_REGISTRY_PREFIX;

/**
 * Handles all registry operations that needs to be done on a eureka service running in an other region.
 *
 * The primary operations include fetching registry information from remote region and fetching delta information
 * on a periodic basis.
 *
 * TODO: a lot of the networking code in this class can be replaced by newer code in
 * {@link com.netflix.discovery.DiscoveryClient}
 *
 * @author Karthik Ranganathan
 *
 */
public class RemoteRegionRegistry implements LookupService<String> {
    private static final Logger logger = LoggerFactory.getLogger(RemoteRegionRegistry.class);

    private final ApacheHttpClient4 discoveryApacheClient;
    private final EurekaJerseyClient discoveryJerseyClient;
    private final com.netflix.servo.monitor.Timer fetchRegistryTimer;
    private final URL remoteRegionURL;

    private final ScheduledExecutorService scheduler;
    // monotonically increasing generation counter to ensure stale threads do not reset registry to an older version
    private final AtomicLong fetchRegistryGeneration = new AtomicLong(0);
    private final Lock fetchRegistryUpdateLock = new ReentrantLock();

    private final AtomicReference<Applications> applications = new AtomicReference<Applications>(new Applications());
    private final AtomicReference<Applications> applicationsDelta = new AtomicReference<Applications>(new Applications());
    private final EurekaServerConfig serverConfig;
    private volatile boolean readyForServingData;
    private final EurekaHttpClient eurekaHttpClient;
    private long timeOfLastSuccessfulRemoteFetch = System.currentTimeMillis();
    private long deltaSuccesses = 0;
    private long deltaMismatches = 0;

    @Inject
    public RemoteRegionRegistry(EurekaServerConfig serverConfig,
                                EurekaClientConfig clientConfig,
                                ServerCodecs serverCodecs,
                                String regionName,
                                URL remoteRegionURL) {
        this.serverConfig = serverConfig;
        this.remoteRegionURL = remoteRegionURL;
        this.fetchRegistryTimer = Monitors.newTimer(this.remoteRegionURL.toString() + "_FetchRegistry");

        EurekaJerseyClientBuilder clientBuilder = new EurekaJerseyClientBuilder()
                .withUserAgent("Java-EurekaClient-RemoteRegion")
                .withEncoderWrapper(serverCodecs.getFullJsonCodec())
                .withDecoderWrapper(serverCodecs.getFullJsonCodec())
                .withConnectionTimeout(serverConfig.getRemoteRegionConnectTimeoutMs())
                .withReadTimeout(serverConfig.getRemoteRegionReadTimeoutMs())
                .withMaxConnectionsPerHost(serverConfig.getRemoteRegionTotalConnectionsPerHost())
                .withMaxTotalConnections(serverConfig.getRemoteRegionTotalConnections())
                .withConnectionIdleTimeout(serverConfig.getRemoteRegionConnectionIdleTimeoutSeconds());

        if (remoteRegionURL.getProtocol().equals("http")) {
            clientBuilder.withClientName("Discovery-RemoteRegionClient-" + regionName);
        } else if ("true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
            clientBuilder.withClientName("Discovery-RemoteRegionSystemSecureClient-" + regionName)
                    .withSystemSSLConfiguration();
        } else {
            clientBuilder.withClientName("Discovery-RemoteRegionSecureClient-" + regionName)
                    .withTrustStoreFile(
                            serverConfig.getRemoteRegionTrustStore(),
                            serverConfig.getRemoteRegionTrustStorePassword()
                    );
        }
        discoveryJerseyClient = clientBuilder.build();
        discoveryApacheClient = discoveryJerseyClient.getClient();

        // should we enable GZip decoding of responses based on Response Headers?
        if (serverConfig.shouldGZipContentFromRemoteRegion()) {
            // compressed only if there exists a 'Content-Encoding' header whose value is "gzip"
            discoveryApacheClient.addFilter(new GZIPContentEncodingFilter(false));
        }

        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("Cannot find localhost ip", e);
        }
        EurekaServerIdentity identity = new EurekaServerIdentity(ip);
        discoveryApacheClient.addFilter(new EurekaIdentityHeaderFilter(identity));

        // Configure new transport layer (candidate for injecting in the future)
        EurekaHttpClient newEurekaHttpClient = null;
        try {
            ClusterResolver clusterResolver = StaticClusterResolver.fromURL(regionName, remoteRegionURL);
            newEurekaHttpClient = EurekaServerHttpClients.createRemoteRegionClient(
                    serverConfig, clientConfig.getTransportConfig(), serverCodecs, clusterResolver);
        } catch (Exception e) {
            logger.warn("Transport initialization failure", e);
        }
        this.eurekaHttpClient = newEurekaHttpClient;

        try {
            if (fetchRegistry()) {
                this.readyForServingData = true;
            } else {
                logger.warn("Failed to fetch remote registry. This means this eureka server is not ready for serving "
                        + "traffic.");
            }
        } catch (Throwable e) {
            logger.error("Problem fetching registry information :", e);
        }

        // remote region fetch
        Runnable remoteRegionFetchTask = new Runnable() {
            @Override
            public void run() {
                try {
                    if (fetchRegistry()) {
                        readyForServingData = true;
                    } else {
                        logger.warn("Failed to fetch remote registry. This means this eureka server is not "
                                + "ready for serving traffic.");
                    }
                } catch (Throwable e) {
                    logger.error(
                            "Error getting from remote registry :", e);
                }
            }
        };

        ThreadPoolExecutor remoteRegionFetchExecutor = new ThreadPoolExecutor(
                1, serverConfig.getRemoteRegionFetchThreadPoolSize(), 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());  // use direct handoff

        scheduler = Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder()
                        .setNameFormat("Eureka-RemoteRegionCacheRefresher_" + regionName + "-%d")
                        .setDaemon(true)
                        .build());

        scheduler.schedule(
                new TimedSupervisorTask(
                        "RemoteRegionFetch_" + regionName,
                        scheduler,
                        remoteRegionFetchExecutor,
                        serverConfig.getRemoteRegionRegistryFetchInterval(),
                        TimeUnit.SECONDS,
                        5,  // exponential backoff bound
                        remoteRegionFetchTask
                ),
                serverConfig.getRemoteRegionRegistryFetchInterval(), TimeUnit.SECONDS);

        try {
            Monitors.registerObject(this);
        } catch (Throwable e) {
            logger.warn("Cannot register the JMX monitor for the RemoteRegionRegistry :", e);
        }
    }

    /**
     * Check if this registry is ready for serving data.
     * @return true if ready, false otherwise.
     */
    public boolean isReadyForServingData() {
        return readyForServingData;
    }

    /**
     * Fetch the registry information from the remote region.
     * @return true, if the fetch was successful, false otherwise.
     */
    private boolean fetchRegistry() {
        boolean success;
        Stopwatch tracer = fetchRegistryTimer.start();

        try {
            // If the delta is disabled or if it is the first time, get all applications
            if (serverConfig.shouldDisableDeltaForRemoteRegions()
                    || (getApplications() == null)
                    || (getApplications().getRegisteredApplications().size() == 0)) {
                logger.info("Disable delta property : {}", serverConfig.shouldDisableDeltaForRemoteRegions());
                logger.info("Application is null : {}", getApplications() == null);
                logger.info("Registered Applications size is zero : {}", getApplications().getRegisteredApplications().isEmpty());
                success = storeFullRegistry();
            } else {
                success = fetchAndStoreDelta();
            }
            logTotalInstances();
        } catch (Throwable e) {
            logger.error("Unable to fetch registry information from the remote registry {}", this.remoteRegionURL, e);
            return false;
        } finally {
            if (tracer != null) {
                tracer.stop();
            }
        }

        if (success) {
            timeOfLastSuccessfulRemoteFetch = System.currentTimeMillis();
        }

        return success;
    }

    private boolean fetchAndStoreDelta() throws Throwable {
        long currGeneration = fetchRegistryGeneration.get();
        Applications delta = fetchRemoteRegistry(true);

        if (delta == null) {
            logger.error("The delta is null for some reason. Not storing this information");
        } else if (fetchRegistryGeneration.compareAndSet(currGeneration, currGeneration + 1)) {
            this.applicationsDelta.set(delta);
        } else {
            delta = null;  // set the delta to null so we don't use it
            logger.warn("Not updating delta as another thread is updating it already");
        }

        if (delta == null) {
            logger.warn("The server does not allow the delta revision to be applied because it is not "
                    + "safe. Hence got the full registry.");
            return storeFullRegistry();
        } else {
            String reconcileHashCode = "";
            if (fetchRegistryUpdateLock.tryLock()) {
                try {
                    updateDelta(delta);
                    reconcileHashCode = getApplications().getReconcileHashCode();
                } finally {
                    fetchRegistryUpdateLock.unlock();
                }
            } else {
                logger.warn("Cannot acquire update lock, aborting updateDelta operation of fetchAndStoreDelta");
            }

            // There is a diff in number of instances for some reason
            if (!reconcileHashCode.equals(delta.getAppsHashCode())) {
                deltaMismatches++;
                return reconcileAndLogDifference(delta, reconcileHashCode);
            } else {
                deltaSuccesses++;
            }
        }

        return delta != null;
    }

    /**
     * Updates the delta information fetches from the eureka server into the
     * local cache.
     *
     * @param delta
     *            the delta information received from eureka server in the last
     *            poll cycle.
     */
    private void updateDelta(Applications delta) {
        int deltaCount = 0;
        for (Application app : delta.getRegisteredApplications()) {
            for (InstanceInfo instance : app.getInstances()) {
                ++deltaCount;
                if (ActionType.ADDED.equals(instance.getActionType())) {
                    Application existingApp = getApplications()
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        getApplications().addApplication(app);
                    }
                    logger.debug("Added instance {} to the existing apps ",
                            instance.getId());
                    getApplications().getRegisteredApplications(
                            instance.getAppName()).addInstance(instance);
                } else if (ActionType.MODIFIED.equals(instance.getActionType())) {
                    Application existingApp = getApplications()
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        getApplications().addApplication(app);
                    }
                    logger.debug("Modified instance {} to the existing apps ",
                            instance.getId());

                    getApplications().getRegisteredApplications(
                            instance.getAppName()).addInstance(instance);

                } else if (ActionType.DELETED.equals(instance.getActionType())) {
                    Application existingApp = getApplications()
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        getApplications().addApplication(app);
                    }
                    logger.debug("Deleted instance {} to the existing apps ",
                            instance.getId());
                    getApplications().getRegisteredApplications(
                            instance.getAppName()).removeInstance(instance);
                }
            }
        }
        logger.debug(
                "The total number of instances fetched by the delta processor : {}",
                deltaCount);

    }

    /**
     * Close HTTP response object and its respective resources.
     *
     * @param response
     *            the HttpResponse object.
     */
    private void closeResponse(ClientResponse response) {
        if (response != null) {
            try {
                response.close();
            } catch (Throwable th) {
                logger.error("Cannot release response resource :", th);
            }
        }
    }

    /**
     * Gets the full registry information from the eureka server and stores it
     * locally.
     *
     * @return the full registry information.
     */
    public boolean storeFullRegistry() {
        long currentGeneration = fetchRegistryGeneration.get();
        Applications apps = fetchRemoteRegistry(false);
        if (apps == null) {
            logger.error("The application is null for some reason. Not storing this information");
        } else if (fetchRegistryGeneration.compareAndSet(currentGeneration, currentGeneration + 1)) {
            applications.set(apps);
            applicationsDelta.set(apps);
            logger.info("Successfully updated registry with the latest content");
            return true;
        } else {
            logger.warn("Not updating applications as another thread is updating it already");
        }
        return false;
    }

    /**
     * Fetch registry information from the remote region.
     * @param delta - true, if the fetch needs to get deltas, false otherwise
     * @return - response which has information about the data.
     */
    private Applications fetchRemoteRegistry(boolean delta) {
        logger.info("Getting instance registry info from the eureka server : {} , delta : {}", this.remoteRegionURL, delta);

        if (shouldUseExperimentalTransport()) {
            try {
                EurekaHttpResponse<Applications> httpResponse = delta ? eurekaHttpClient.getDelta() : eurekaHttpClient.getApplications();
                int httpStatus = httpResponse.getStatusCode();
                if (httpStatus >= 200 && httpStatus < 300) {
                    logger.debug("Got the data successfully : {}", httpStatus);
                    return httpResponse.getEntity();
                }
                logger.warn("Cannot get the data from {} : {}", this.remoteRegionURL, httpStatus);
            } catch (Throwable t) {
                logger.error("Can't get a response from {}", this.remoteRegionURL, t);
            }
        } else {
            ClientResponse response = null;
            try {
                String urlPath = delta ? "apps/delta" : "apps/";

                response = discoveryApacheClient.resource(this.remoteRegionURL + urlPath)
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .get(ClientResponse.class);
                int httpStatus = response.getStatus();
                if (httpStatus >= 200 && httpStatus < 300) {
                    logger.debug("Got the data successfully : {}", httpStatus);
                    return response.getEntity(Applications.class);
                }
                logger.warn("Cannot get the data from {} : {}", this.remoteRegionURL, httpStatus);
            } catch (Throwable t) {
                logger.error("Can't get a response from {}", this.remoteRegionURL, t);
            } finally {
                closeResponse(response);
            }
        }
        return null;
    }

    /**
     * Reconciles the delta information fetched to see if the hashcodes match.
     *
     * @param delta - the delta information fetched previously for reconciliation.
     * @param reconcileHashCode - the hashcode for comparison.
     * @return - response
     * @throws Throwable
     */
    private boolean reconcileAndLogDifference(Applications delta, String reconcileHashCode) throws Throwable {
        logger.warn("The Reconcile hashcodes do not match, client : {}, server : {}. Getting the full registry",
                reconcileHashCode, delta.getAppsHashCode());

        long currentGeneration = fetchRegistryGeneration.get();

        Applications apps = this.fetchRemoteRegistry(false);
        if (apps == null) {
            logger.error("The application is null for some reason. Not storing this information");
            return false;
        }

        if (fetchRegistryGeneration.compareAndSet(currentGeneration, currentGeneration + 1)) {
            applications.set(apps);
            applicationsDelta.set(apps);
            logger.warn("The Reconcile hashcodes after complete sync up, client : {}, server : {}.",
                    getApplications().getReconcileHashCode(),
                    delta.getAppsHashCode());
            return true;
        }else {
            logger.warn("Not setting the applications map as another thread has advanced the update generation");
            return true;  // still return true
        }
    }

    /**
     * Logs the total number of non-filtered instances stored locally.
     */
    private void logTotalInstances() {
        int totInstances = 0;
        for (Application application : getApplications().getRegisteredApplications()) {
            totInstances += application.getInstancesAsIsFromEureka().size();
        }
        logger.debug("The total number of all instances in the client now is {}", totInstances);
    }

    @Override
    public Applications getApplications() {
        return applications.get();
    }

    @Override
    public InstanceInfo getNextServerFromEureka(String arg0, boolean arg1) {
        return null;
    }

    @Override
    public Application getApplication(String appName) {
        return this.applications.get().getRegisteredApplications(appName);
    }

    @Override
    public List<InstanceInfo> getInstancesById(String id) {
        List<InstanceInfo> list = new ArrayList<>(1);

        for (Application app : applications.get().getRegisteredApplications()) {
            InstanceInfo info = app.getByInstanceId(id);
            if (info != null) {
                list.add(info);
                return list;
            }
        }
        return Collections.emptyList();
    }

    public Applications getApplicationDeltas() {
        return this.applicationsDelta.get();
    }

    private boolean shouldUseExperimentalTransport() {
        if (eurekaHttpClient == null) {
            return false;
        }
        String enabled = serverConfig.getExperimental("transport.enabled");
        return enabled != null && "true".equalsIgnoreCase(enabled);
    }

    @com.netflix.servo.annotations.Monitor(name = METRIC_REGISTRY_PREFIX + "secondsSinceLastSuccessfulRemoteFetch", type = DataSourceType.GAUGE)
    public long getTimeOfLastSuccessfulRemoteFetch() {
        return (System.currentTimeMillis() - timeOfLastSuccessfulRemoteFetch) / 1000;
    }

    @com.netflix.servo.annotations.Monitor(name = METRIC_REGISTRY_PREFIX + "remoteDeltaSuccesses", type = DataSourceType.COUNTER)
    public long getRemoteFetchSuccesses() {
        return deltaSuccesses;
    }

    @com.netflix.servo.annotations.Monitor(name = METRIC_REGISTRY_PREFIX + "remoteDeltaMismatches", type = DataSourceType.COUNTER)
    public long getRemoteFetchMismatches() {
        return deltaMismatches;
    }
}
