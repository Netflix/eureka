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

package com.netflix.discovery;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.naming.directory.DirContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.EurekaJerseyClient;
import com.netflix.discovery.shared.EurekaJerseyClient.JerseyClient;
import com.netflix.discovery.shared.LookupService;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;

/**
 * The class that is instrumental for interactions with <tt>Eureka Server</tt>.
 * 
 * <p>
 * <tt>Eureka Client</tt> is responsible for a) <em>Registering</em> the
 * instance with <tt>Eureka Server</tt> b) <em>Renewal</em>of the lease with
 * <tt>Eureka Server</tt> c) <em>Cancellation</em> of the lease from
 * <tt>Eureka Server</tt> during shutdown
 * <p>
 * d) <em>Querying</em> the list of services/instances registered with
 * <tt>Eureka Server</tt>
 * <p>
 * 
 * <p>
 * <tt>Eureka Client</tt> needs a configured list of <tt>Eureka Server</tt>
 * {@link URL}s to talk to.These {@link URL}s are typically amazon elastic eips
 * which do not change. All of the functions defined above fail-over to other
 * {@link URL}s specified in the list in the case of failure.
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
public class DiscoveryClient implements LookupService {
    private static final Logger logger = LoggerFactory
            .getLogger(DiscoveryClient.class);

    private static final DynamicPropertyFactory configInstance = DynamicPropertyFactory
            .getInstance();

    // Constants
    private static final String DNS_PROVIDER_URL = "dns:";
    private static final String DNS_NAMING_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final String JAVA_NAMING_FACTORY_INITIAL = "java.naming.factory.initial";
    private static final String JAVA_NAMING_PROVIDER_URL = "java.naming.provider.url";
    private static final String DNS_RECORD_TYPE = "TXT";
    private static final String VALUE_DELIMITER = ",";
    private static final String COMMA_STRING = VALUE_DELIMITER;
    private static final String DISCOVERY_APPID = "DISCOVERY";
    private static final String UNKNOWN = "UNKNOWN";
    private static final DirContext dirContext = DiscoveryClient.getDirContext();

    // Timers
    private String PREFIX = "DiscoveryClient_";
    private final com.netflix.servo.monitor.Timer GET_SERVICE_URLS_DNS_TIMER = Monitors
            .newTimer(PREFIX + "GetServiceUrlsFromDNS");
    private final com.netflix.servo.monitor.Timer REGISTER_TIMER = Monitors
            .newTimer(PREFIX + "Register");
    private final com.netflix.servo.monitor.Timer REFRESH_TIMER = Monitors
            .newTimer(PREFIX + "Refresh");
    private final com.netflix.servo.monitor.Timer REFRESH_DELTA_TIMER = Monitors
            .newTimer(PREFIX + "RefreshDelta");
    private final com.netflix.servo.monitor.Timer RENEW_TIMER = Monitors
            .newTimer(PREFIX + "Renew");
    private final com.netflix.servo.monitor.Timer CANCEL_TIMER = Monitors
            .newTimer(PREFIX + "Cancel");
    private final com.netflix.servo.monitor.Timer FETCH_REGISTRY_TIMER = Monitors
            .newTimer(PREFIX + "FetchRegistry");
    private final Counter SERVER_RETRY_COUNTER = Monitors.newCounter(PREFIX
            + "Retry");
    private final Counter ALL_SERVER_FAILURE_COUNT = Monitors.newCounter(PREFIX
            + "Failed");
    private final Counter REREGISTER_COUNTER = Monitors.newCounter(PREFIX
            + "Reregister");

    // instance variables
    private volatile HealthCheckCallback healthCheckCallback;
    private volatile AtomicReference<List<String>> eurekaServiceUrls = new AtomicReference<List<String>>();
    private volatile AtomicReference<Applications> localRegionApps = new AtomicReference<Applications>();
    private volatile Map<String, Applications> remoteRegionVsApps = new ConcurrentHashMap<String, Applications>();

    private InstanceInfo instanceInfo;
    private String appPathIdentifier;
    private boolean isRegisteredWithDiscovery = false;
    private String discoveryServerAMIId;
    private JerseyClient discoveryJerseyClient;
    private ApacheHttpClient4 discoveryApacheClient;
    private static EurekaClientConfig clientConfig;
    private final AtomicReference<String> remoteRegionsToFetch;
    private final InstanceRegionChecker instanceRegionChecker;

    private enum Action {
        Register, Cancel, Renew, Refresh, Refresh_Delta
    }

    private Timer cacheRefreshTimer = new Timer(PREFIX + "CacheRefresher", true);
    private Timer heartbeatTimer = new Timer(PREFIX + "Heartbeat", true);
    private Timer serviceUrlUpdaterTimer = new Timer(PREFIX
            + "ServiceURLUpdater", true);
    private Timer instanceInfoReplicatorTimer = new Timer(PREFIX
            + "InstanceInfo-Replictor", true);

    DiscoveryClient(InstanceInfo myInfo, EurekaClientConfig config) {
        try {
            clientConfig = config;
            final String zone = getZone(myInfo);
            eurekaServiceUrls.set(getDiscoveryServiceUrls(zone));
            serviceUrlUpdaterTimer.schedule(getServiceUrlUpdateTask(zone),
                    clientConfig.getEurekaServiceUrlPollIntervalSeconds(),
                    clientConfig.getEurekaServiceUrlPollIntervalSeconds());
            localRegionApps.set(new Applications());

            if (myInfo != null) {
                instanceInfo = myInfo;
                appPathIdentifier = instanceInfo.getAppName() + "/"
                        + instanceInfo.getId();
            }
            String proxyHost = clientConfig.getProxyHost();
            String proxyPort = clientConfig.getProxyPort();
            discoveryJerseyClient = EurekaJerseyClient.createJerseyClient(
                    clientConfig.getEurekaServerConnectTimeoutSeconds(),
                    clientConfig.getEurekaServerReadTimeoutSeconds(),
                    clientConfig.getEurekaServerTotalConnectionsPerHost(),
                    clientConfig.getEurekaServerTotalConnections(),
                    clientConfig.getEurekaConnectionIdleTimeoutSeconds());
            discoveryApacheClient = discoveryJerseyClient.getClient();
            ClientConfig cc = discoveryJerseyClient.getClientconfig();
            remoteRegionsToFetch = new AtomicReference<String>(clientConfig.fetchRegistryForRemoteRegions());
            AzToRegionMapper azToRegionMapper;
            if (clientConfig.shouldUseDnsForFetchingServiceUrls()) {
                azToRegionMapper = new DNSBasedAzToRegionMapper();
            } else {
                azToRegionMapper = new PropertyBasedAzToRegionMapper(clientConfig);
            }
            if (null != remoteRegionsToFetch.get()) {
                azToRegionMapper.setRegionsToFetch(remoteRegionsToFetch.get().split(","));
            }
            instanceRegionChecker = new InstanceRegionChecker(azToRegionMapper, clientConfig.getRegion());
            boolean enableGZIPContentEncodingFilter = config.shouldGZipContent();
            // should we enable GZip decoding of responses based on Response
            // Headers?
            if (enableGZIPContentEncodingFilter) {
                // compressed only if there exists a 'Content-Encoding' header
                // whose value is "gzip"
                discoveryApacheClient.addFilter(new GZIPContentEncodingFilter(
                        false));
            }
            if (proxyHost != null && proxyPort != null) {
                cc.getProperties().put(
                        DefaultApacheHttpClient4Config.PROPERTY_PROXY_URI,
                        "http://" + proxyHost + ":" + proxyPort);
            }

        } catch (Throwable e) {
            throw new RuntimeException("Failed to initialize DiscoveryClient!",
                    e);
        }
        if (!fetchRegistry(false)) {
            fetchRegistryFromBackup();
        }
        initScheduledTasks();
        try {
            Monitors.registerObject(this);
        } catch (Throwable e) {
            logger.warn("Cannot register timers", e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.discovery.shared.LookupService#getApplication(java.lang.String)
     */
    public Application getApplication(String appName) {
        return getApplications().getRegisteredApplications(appName);
    }

    /**
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.shared.LookupService#getApplications()
     */
    public Applications getApplications() {
        return localRegionApps.get();
    }

    public Applications getApplicationsForARegion(@Nullable String region) {
        if (instanceRegionChecker.isLocalRegion(region)) {
            return localRegionApps.get();
        } else {
            return remoteRegionVsApps.get(region);
        }
    }

    public Set<String> getAllKnownRegions() {
        String localRegion = instanceRegionChecker.getLocalRegion();
        if (!remoteRegionVsApps.isEmpty()) {
            Set<String> regions = remoteRegionVsApps.keySet();
            Set<String> toReturn = new HashSet<String>(regions);
            toReturn.add(localRegion);
            return toReturn;
        } else {
            return Collections.singleton(localRegion);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.discovery.shared.LookupService#getInstancesById(java.lang.String)
     */
    public List<InstanceInfo> getInstancesById(String id) {
        List<InstanceInfo> instancesList = new ArrayList<InstanceInfo>();
        for (Application app : this.getApplications()
                .getRegisteredApplications()) {
            InstanceInfo instanceInfo = app.getByInstanceId(id);
            if (instanceInfo != null) {
                instancesList.add(instanceInfo);
            }
        }
        return instancesList;
    }

    /**
     * Register {@link HealthCheckCallback} with the eureka client.
     * 
     * Once registered, the eureka client will invoke the
     * {@link HealthCheckCallback} in intervals specified by
     * {@link EurekaClientConfig#getInstanceInfoReplicationIntervalSeconds()}.
     * 
     * @param callback
     *            -- app specific healthcheck.
     */
    public void registerHealthCheckCallback(HealthCheckCallback callback) {
        if (instanceInfo == null) {
            logger.error("Cannot register a listener for instance info since it is null!");
        }
        if (callback != null) {
            healthCheckCallback = callback;
        }
    }

    /**
     * Gets the list of instances matching the given VIP Address.
     * 
     * @param vipAddress
     *            - The VIP address to match the instances for.
     * @param secure
     *            - true if it is a secure vip address, false otherwise
     * @return - The list of {@link InstanceInfo} objects matching the criteria
     */
    public List<InstanceInfo> getInstancesByVipAddress(String vipAddress, boolean secure) {
        return getInstancesByVipAddress(vipAddress, secure, instanceRegionChecker.getLocalRegion());
    }

    /**
     * Gets the list of instances matching the given VIP Address in the passed region.
     *
     * @param vipAddress - The VIP address to match the instances for.
     * @param secure - true if it is a secure vip address, false otherwise
     * @param region - region from which the instances are to be fetched. If <code>null</code> then local region is
     *               assumed.
     *
     * @return - The list of {@link InstanceInfo} objects matching the criteria, empty list if not instances found.
     */
    public List<InstanceInfo> getInstancesByVipAddress(String vipAddress, boolean secure,
                                                       @Nullable String region) {
        if (vipAddress == null) {
            throw new IllegalArgumentException(
                    "Supplied VIP Address cannot be null");
        }
        Applications applications;
        if (instanceRegionChecker.isLocalRegion(region)) {
            applications = this.localRegionApps.get();
        } else {
            applications = remoteRegionVsApps.get(region);
            if (null == applications) {
                logger.debug("No applications are defined for region {}, so returning an empty instance list for vip address {}.",
                             region, vipAddress);
                return Collections.emptyList();
            }
        }

        if (!secure) {
            return applications.getInstancesByVirtualHostName(vipAddress);
        } else {
            return applications.getInstancesBySecureVirtualHostName(vipAddress);

        }

    }

    /**
     * Gets the list of instances matching the given VIP Address and the given
     * application name if both of them are not null. If one of them is null,
     * then that criterion is completely ignored for matching instances.
     * 
     * @param vipAddress
     *            - The VIP address to match the instances for.
     * @param appName
     *            - The applicationName to match the instances for.
     * @param secure
     *            - true if it is a secure vip address, false otherwise.
     * @return - The list of {@link InstanceInfo} objects matching the criteria.
     */
    public List<InstanceInfo> getInstancesByVipAddressAndAppName(
            String vipAddress, String appName, boolean secure) {

        List<InstanceInfo> result = new ArrayList<InstanceInfo>();
        if (vipAddress == null && appName == null) {
            throw new IllegalArgumentException(
                    "Supplied VIP Address and application name cannot both be null");
        } else if (vipAddress != null && appName == null) {
            return getInstancesByVipAddress(vipAddress, secure);
        } else if (vipAddress == null && appName != null) {
            Application application = getApplication(appName);
            if (application != null) {
                result = application.getInstances();
            }
            return result;
        }

        String instanceVipAddress;
        for (Application app : getApplications().getRegisteredApplications()) {
            for (InstanceInfo instance : app.getInstances()) {
                if (secure) {
                    instanceVipAddress = instance.getSecureVipAddress();
                } else {
                    instanceVipAddress = instance.getVIPAddress();
                }
                if (instanceVipAddress == null) {
                    continue;
                }
                String[] instanceVipAddresses = instanceVipAddress
                        .split(COMMA_STRING);

                // If the VIP Address is delimited by a comma, then consider to
                // be a list of VIP Addresses.
                // Try to match at least one in the list, if it matches then
                // return the instance info for the same
                for (String vipAddressFromList : instanceVipAddresses) {
                    if (vipAddress.equalsIgnoreCase(vipAddressFromList.trim())
                            && appName.equalsIgnoreCase(instance.getAppName())) {
                        result.add(instance);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.discovery.shared.LookupService#getNextServerFromEureka(java
     * .lang.String, boolean)
     */
    public InstanceInfo getNextServerFromEureka(String virtualHostname, boolean secure) {
        List<InstanceInfo> instanceInfoList = this.getInstancesByVipAddress(
                virtualHostname, secure);
        if (instanceInfoList == null || instanceInfoList.isEmpty()) {
            throw new RuntimeException("No matches for the virtual host name :"
                    + virtualHostname);
        }
        Applications apps = this.localRegionApps.get();
        int index = (int) (apps.getNextIndex(virtualHostname.toUpperCase(),
                secure).incrementAndGet() % instanceInfoList.size());
        return instanceInfoList.get(index);
    }

    /**
     * Get all applications registered with a specific eureka service.
     * 
     * @param serviceUrl
     *            - The string representation of the service url.
     * @return - The registry information containing all applications.
     */
    public Applications getApplications(String serviceUrl) {
        ClientResponse response = null;
        Applications apps = null;
        try {
            response = getUrl(serviceUrl + "apps/");
            apps = response.getEntity(Applications.class);
            logger.debug(PREFIX + appPathIdentifier + " -  refresh status: "
                    + response.getStatus());
            return apps;
        } catch (Exception e) {
            logger.error(
                    PREFIX + appPathIdentifier
                            + " - was unable to refresh it's cache! status = "
                            + e.getMessage(), e);

        } finally {
            if (response != null) {
                response.close();
            }
        }
        return apps;
    }

    /**
     * Checks to see if the eureka client registration is enabled.
     * 
     * @param myInfo
     *            - The instance info object
     * @return - true, if the instance should be registered with eureka, false
     *         otherwise
     */
    private boolean shouldRegister(InstanceInfo myInfo) {
        boolean shouldRegister = clientConfig.shouldRegisterWithEureka();
        if (!shouldRegister) {
            return false;
        } else if ((myInfo != null)
                && (myInfo.getDataCenterInfo()
                        .equals(DataCenterInfo.Name.Amazon))) {
            return true;
        }

        return shouldRegister;
    }

    /**
     * Register with the eureka service by making the appropriate REST call.
     */
    void register() {
        logger.info(PREFIX + appPathIdentifier + ": registering service...");
        ClientResponse response = null;
        try {
            response = makeRemoteCall(Action.Register);
            isRegisteredWithDiscovery = true;
            logger.info(PREFIX + appPathIdentifier + " - registration status: "
                    + (response != null ? response.getStatus() : "not sent"));

        } catch (Throwable e) {
            logger.error(PREFIX + appPathIdentifier + " - registration failed"
                    + e.getMessage(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Get the list of all eureka service urls from properties file for the
     * eureka client to talk to
     * 
     * @param instanceZone
     *            - The zone in which the client resides
     * @param preferSameZone
     *            - true if we have to prefer the same zone as the client, false
     *            otherwise
     * @return - The list of all eureka service urls for the eureka client to
     *         talk to
     */
    public static List<String> getEurekaServiceUrlsFromConfig(
            String instanceZone, boolean preferSameZone) {
        List<String> orderedUrls = new ArrayList<String>();
        String region = getRegion();
        String availZones[] = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        if (availZones == null || availZones.length == 0) {
            availZones = new String[1];
            availZones[0] = "default";
        }
        logger.debug("The availability zone for the given region {} are %s",
                region, Arrays.toString(availZones));
        int myZoneOffset = getZoneOffset(instanceZone, preferSameZone,
                availZones);

        List<String> serviceUrls = clientConfig
                .getEurekaServerServiceUrls(availZones[myZoneOffset]);
        if (serviceUrls != null) {
            orderedUrls.addAll(serviceUrls);
        }
        int currentOffset = myZoneOffset == (availZones.length - 1) ? 0
                : (myZoneOffset + 1);
        while (currentOffset != myZoneOffset) {
            serviceUrls = clientConfig
                    .getEurekaServerServiceUrls(availZones[currentOffset]);
            if (serviceUrls != null) {
                orderedUrls.addAll(serviceUrls);
            }
            if (currentOffset == (availZones.length - 1)) {
                currentOffset = 0;
            } else {
                currentOffset++;
            }
        }

        if (orderedUrls.size() < 1) {
            throw new IllegalArgumentException(
                    "DiscoveryClient: invalid serviceUrl specified!");
        }
        return orderedUrls;
    }

    /**
     * Shuts down Eureka Client. Also sends a deregistration request to the
     * eureka server.
     */
    public void shutdown() {
        // If APPINFO was registered
        if (instanceInfo != null && shouldRegister(instanceInfo)) {
            instanceInfo.setStatus(InstanceStatus.DOWN);
            unregister();
        } else {
            if (null != cacheRefreshTimer) {
                cacheRefreshTimer.cancel();
            }
        }
    }

    /**
     * unregister w/ the eureka service
     */
    void unregister() {
        ClientResponse response = null;
        try {
            response = makeRemoteCall(Action.Cancel);

            logger.info(PREFIX
                    + appPathIdentifier
                    + " - deregister  status: "
                    + (response != null ? response.getStatus()
                            : "not registered"));
        } catch (Throwable e) {
            logger.error(PREFIX + appPathIdentifier
                    + " - de-registration failed" + e.getMessage(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Fetches the registry information.
     * 
     * <p>
     * This method tries to get only deltas after the first fetch unless there
     * is an issue in reconciling eureka server and client registry information.
     * </p>
     *
     * @param forceFullRegistryFetch Forces a full registry fetch.
     *
     * @return
     */
    private boolean fetchRegistry(boolean forceFullRegistryFetch) {
        ClientResponse response = null;
        Stopwatch tracer = FETCH_REGISTRY_TIMER.start();

        try {
            // If the delta is disabled or if it is the first time, get all
            // applications
            Applications applications = getApplications();

            if (clientConfig.shouldDisableDelta() || forceFullRegistryFetch || (applications == null)
                    || (applications.getRegisteredApplications().size() == 0)
                    // The client application does not have the latest library
                    // supporting delta
                    || (applications.getVersion() == -1)) {
                logger.info("Disable delta property : {}", clientConfig.shouldDisableDelta());
                logger.info("Force full registry fetch : {}", forceFullRegistryFetch);
                logger.info("Application is null : {}", (applications == null));
                logger.info("Registered Applications size is zero : {}", (applications.getRegisteredApplications().size() == 0));
                logger.info("Application version is -1: {}", (applications.getVersion() == -1));
                response = getAndStoreFullRegistry();
            } else {
                Applications delta = null;
                response = makeRemoteCall(Action.Refresh_Delta);
                if (response.getStatus() == Status.OK.getStatusCode()) {
                    delta = response.getEntity(Applications.class);
                }
                if (delta == null) {
                    logger.warn("The server does not allow the delta revision to be applied because it is not safe. Hence got the full registry.");
                    this.closeResponse(response);
                    response = getAndStoreFullRegistry();
                } else {
                    updateDelta(delta);
                    String reconcileHashCode = getReconcileHashCode(applications);
                    // There is a diff in number of instances for some reason
                    if ((!reconcileHashCode.equals(delta.getAppsHashCode()))
                            || clientConfig.shouldLogDeltaDiff()) {
                        response = reconcileAndLogDifference(response, delta,
                                reconcileHashCode);

                    }
                }
                logTotalInstances();
            }
            logger.debug(PREFIX + appPathIdentifier + " -  refresh status: "
                    + response.getStatus());
        } catch (Throwable e) {
            logger.error(
                    PREFIX + appPathIdentifier
                            + " - was unable to refresh it's cache! status = "
                            + e.getMessage(), e);
            return false;

        } finally {
            if (tracer != null) {
                tracer.stop();
            }
            closeResponse(response);
        }
        return true;
    }

    private String getReconcileHashCode(Applications applications) {
        TreeMap<String, AtomicInteger> instanceCountMap = new TreeMap<String, AtomicInteger>();
        if (isFetchingRemoteRegionRegistries()) {
            for (Applications remoteApp : remoteRegionVsApps.values()) {
                remoteApp.populateInstanceCountMap(instanceCountMap);
            }
        }
        applications.populateInstanceCountMap(instanceCountMap);
        return Applications.getReconcileHashCode(instanceCountMap);
    }

    /**
     * Gets the full registry information from the eureka server and stores it
     * locally.
     * 
     * @return the full registry information.
     * @throws Throwable
     *             on error.
     */
    private ClientResponse getAndStoreFullRegistry() throws Throwable {
        ClientResponse response;
        response = makeRemoteCall(Action.Refresh);
        logger.info("Getting all instance registry info from the eureka server");
        Applications apps = response.getEntity(Applications.class);
        if (apps == null) {
            logger.error("The application is null for some reason. Not storing this information");
        }
        else {
            localRegionApps.set(this.filterAndShuffle(apps));
        }
        logger.info("The response status is {}", response.getStatus());
        return response;
    }

    /**
     * Logs the total number of instances stored locally.
     */
    private void logTotalInstances() {
        int totInstances = 0;
        for (Application application : getApplications()
                .getRegisteredApplications()) {
            totInstances += application.getInstances().size();
        }
        logger.debug("The total number of instances in the client now is {}",
                totInstances);
    }

    /**
     * Reconcile the eureka server and client registry information and logs the
     * differences if any.
     * 
     * @param response
     *            the HTTP response after getting the full registry.
     * @param delta
     *            the last delta registry information received from the eureka
     *            server.
     * @param reconcileHashCode
     *            the hashcode generated by the server for reconciliation.
     * @return ClientResponse the HTTP response object.
     * @throws Throwable
     *             on any error.
     */
    private ClientResponse reconcileAndLogDifference(ClientResponse response,
            Applications delta, String reconcileHashCode) throws Throwable {
        logger.warn(
                "The Reconcile hashcodes do not match, client : {}, server : {}. Getting the full registry",
                reconcileHashCode, delta.getAppsHashCode());

        this.closeResponse(response);
        response = makeRemoteCall(Action.Refresh);
        Applications serverApps = response.getEntity(Applications.class);
        try {
            Map<String, List<String>> reconcileDiffMap = getApplications().getReconcileMapDiff(serverApps);
            String reconcileString = "";
            for (Map.Entry<String, List<String>> mapEntry : reconcileDiffMap.entrySet()) {
                reconcileString = reconcileString + mapEntry.getKey() + ": ";
                for (String displayString : mapEntry.getValue()) {
                    reconcileString = reconcileString + displayString;
                }
                reconcileString = reconcileString + "\n";
            }
            logger.warn("The reconcile string is {}", reconcileString);
        } catch (Throwable e) {
            logger.error("Could not calculate reconcile string ", e);
        }
        localRegionApps.set(this.filterAndShuffle(serverApps));
        getApplications().setVersion(delta.getVersion());
        logger.warn(
                "The Reconcile hashcodes after complete sync up, client : {}, server : {}.",
                getApplications().getReconcileHashCode(),
                delta.getAppsHashCode());
        return response;
    }

    /**
     * Updates the delta information fetches from the eureka server into the
     * local cache.
     * 
     * @param delta
     *            the delta information received from eureka server in the last
     *            poll cycle.
     */
    private void
    updateDelta(Applications delta) {
        int deltaCount = 0;
        for (Application app : delta.getRegisteredApplications()) {
            for (InstanceInfo instance : app.getInstances()) {
                Applications applications = getApplications();
                String instanceRegion = instanceRegionChecker.getInstanceRegion(instance);
                if (!instanceRegionChecker.isLocalRegion(instanceRegion)) {
                    Applications remoteApps = remoteRegionVsApps.get(instanceRegion);
                    if (null == remoteApps) {
                        remoteApps = new Applications();
                        remoteRegionVsApps.put(instanceRegion, remoteApps);
                    }
                    applications = remoteApps;
                }

                ++deltaCount;
                if (ActionType.ADDED.equals(instance.getActionType())) {
                    Application existingApp = applications
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        applications.addApplication(app);
                    }
                    logger.debug("Added instance {} to the existing apps in region {}",
                            instance.getId(), instanceRegion);
                    applications.getRegisteredApplications(
                            instance.getAppName()).addInstance(instance);
                } else if (ActionType.MODIFIED.equals(instance.getActionType())) {
                    Application existingApp = applications
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        applications.addApplication(app);
                    }
                    logger.debug("Modified instance {} to the existing apps ",
                                 instance.getId());

                    applications.getRegisteredApplications(
                            instance.getAppName()).addInstance(instance);

                } else if (ActionType.DELETED.equals(instance.getActionType())) {
                    Application existingApp = applications
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        applications.addApplication(app);
                    }
                    logger.debug("Deleted instance {} to the existing apps ",
                                 instance.getId());
                    applications.getRegisteredApplications(
                            instance.getAppName()).removeInstance(instance);
                }
            }
        }
        logger.debug(
                "The total number of instances fetched by the delta processor : {}",
                deltaCount);

        getApplications().setVersion(delta.getVersion());
        getApplications().shuffleInstances(clientConfig.shouldFilterOnlyUpInstances());

        for (Applications applications : remoteRegionVsApps.values()) {
            applications.setVersion(delta.getVersion());
            applications.shuffleInstances(clientConfig.shouldFilterOnlyUpInstances());
        }
    }

    /**
     * Makes remote calls with the corresponding action(register,renew etc).
     * 
     * @param action
     *            the action to be performed on eureka server.
     * @return ClientResponse the HTTP response object.
     * @throws Throwable
     *             on any error.
     */
    private ClientResponse makeRemoteCall(Action action) throws Throwable {
        return makeRemoteCall(action, 0);
    }

    /**
     * Makes remote calls with the corresponding action(register,renew etc).
     * 
     * @param action
     *            the action to be performed on eureka server.
     * 
     *            Try the fallback servers in case of problems communicating to
     *            the primary one.
     * 
     * @return ClientResponse the HTTP response object.
     * @throws Throwable
     *             on any error.
     */
    private ClientResponse makeRemoteCall(Action action, int serviceUrlIndex)
            throws Throwable {
        String urlPath = null;
        Stopwatch tracer = null;
        String serviceUrl = eurekaServiceUrls.get().get(serviceUrlIndex);
        ClientResponse response = null;
        logger.debug("Discovery Client talking to the server {}", serviceUrl);
        try {
            // If the application is unknown do not register/renew/cancel but
            // refresh
            if ((UNKNOWN.equals(instanceInfo.getAppName())
                    && (!Action.Refresh.equals(action)) && (!Action.Refresh_Delta
                    .equals(action)))) {
                return null;
            }
            WebResource r = discoveryApacheClient.resource(serviceUrl);
            switch (action) {
            case Renew:
                tracer = RENEW_TIMER.start();
                urlPath = "apps/" + appPathIdentifier;
                response = r
                        .path(urlPath)
                        .queryParam("status",
                                instanceInfo.getStatus().toString())
                        .queryParam("lastDirtyTimestamp",
                                instanceInfo.getLastDirtyTimestamp().toString())
                        .put(ClientResponse.class);
                break;
            case Refresh:
                tracer = REFRESH_TIMER.start();
                urlPath = "apps/";
                if (isFetchingRemoteRegionRegistries()) {
                    urlPath += "?regions=" + remoteRegionsToFetch;
                }
                response = getUrl(serviceUrl + urlPath);
                break;
            case Refresh_Delta:
                tracer = REFRESH_DELTA_TIMER.start();
                urlPath = "apps/delta";
                if (isFetchingRemoteRegionRegistries()) {
                    urlPath += "?regions=" + remoteRegionsToFetch;
                }
                response = getUrl(serviceUrl + urlPath);
                break;
            case Register:
                tracer = REGISTER_TIMER.start();
                urlPath = "apps/" + instanceInfo.getAppName();
                response = r.path(urlPath)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .post(ClientResponse.class, instanceInfo);
                break;
            case Cancel:
                tracer = CANCEL_TIMER.start();
                urlPath = "apps/" + appPathIdentifier;
                response = r.path(urlPath).delete(ClientResponse.class);
                // Return without during de-registration if it is not registered
                // already and if we get a 404
                if ((!isRegisteredWithDiscovery)
                        && (response.getStatus() == Status.NOT_FOUND
                                .getStatusCode())) {
                    return response;
                }
                break;
            }

            if (logger.isInfoEnabled()) {
                logger.info("Finished a call to service url {} and url path {} with status code {}.",
                            new String[] {serviceUrl, urlPath, String.valueOf(response.getStatus())});
            }
            if (isOk(action, response.getStatus())) {
                return response;
            } else {
                logger.warn("Action: " + action + "  => returned status of "
                        + response.getStatus() + " from " + serviceUrl
                        + urlPath);
                throw new RuntimeException("Bad status: "
                        + response.getStatus());
            }
        } catch (Throwable t) {
            closeResponse(response);
            String msg = "Can't get a response from " + serviceUrl + urlPath;
            if (eurekaServiceUrls.get().size() > (++serviceUrlIndex)) {
                logger.warn(msg, t);
                logger.warn("Trying backup: "
                        + eurekaServiceUrls.get().get(serviceUrlIndex));
                SERVER_RETRY_COUNTER.increment();
                return makeRemoteCall(action, serviceUrlIndex);
            } else {
                ALL_SERVER_FAILURE_COUNT.increment();
                logger.error(
                        msg
                                + "\nCan't contact any eureka nodes - possibly a security group issue?",
                        t);
                throw t;
            }
        } finally {
            if (tracer != null) {
                tracer.stop();
            }
        }
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
     * Initializes all scheduled tasks.
     */
    private void initScheduledTasks() {
        // Registry fetch timer
        cacheRefreshTimer.schedule(new CacheRefreshThread(),
                (clientConfig.getRegistryFetchIntervalSeconds() * 1000),
                (clientConfig.getRegistryFetchIntervalSeconds() * 1000));

        if (shouldRegister(instanceInfo)) {
            logger.info("Starting heartbeat executor: " + "renew interval is: "
                    + instanceInfo.getLeaseInfo().getRenewalIntervalInSecs());

            // Heartbeat timer
            heartbeatTimer
                    .schedule(new HeartbeatThread(), instanceInfo
                            .getLeaseInfo().getRenewalIntervalInSecs() * 1000,
                            instanceInfo.getLeaseInfo()
                                    .getRenewalIntervalInSecs() * 1000);

            // InstanceInfo replication timer
            instanceInfoReplicatorTimer
                    .schedule(
                            new InstanceInfoReplicator(),
                            (10 * 1000)
                                    + (clientConfig
                                            .getInstanceInfoReplicationIntervalSeconds() * 1000),
                            (clientConfig
                                    .getInstanceInfoReplicationIntervalSeconds() * 1000));

        }
    }

    /**
     * Get the list of all eureka service urls from DNS for the eureka client to
     * talk to.
     * 
     * @param instanceZone
     *            - The zone in which the client resides.
     * @param preferSameZone
     *            - true if we have to prefer the same zone as the client, false
     *            otherwise.
     * @return - The list of all eureka service urls for the eureka client to
     *         talk to.
     */
    public List<String> getServiceUrlsFromDNS(String instanceZone,
            boolean preferSameZone) {
        Stopwatch t = GET_SERVICE_URLS_DNS_TIMER.start();
        String region = getRegion();
        // Get zone-specific DNS names for the given region so that we can get a
        // list of available zones
        Map<String, List<String>> zoneDnsNamesMap = getZoneBasedDiscoveryUrlsFromRegion(region);
        Set<String> availableZones = zoneDnsNamesMap.keySet();
        List<String> zones = new LinkedList<String>(availableZones);
        int zoneIndex = 0;
        boolean zoneFound = false;
        for (String zone : zones) {
            logger.debug(
                    "Checking if the instance zone {} is the same as the zone from DNS {}",
                    instanceZone, zone);
            if (preferSameZone) {
                if (instanceZone.equalsIgnoreCase(zone)) {
                    zoneFound = true;
                }
            } else {
                if (!instanceZone.equalsIgnoreCase(zone)) {
                    zoneFound = true;
                }
            }
            if (zoneFound) {
                Object[] args = { zones, instanceZone, zoneIndex };
                logger.debug(
                        "The zone index from the list {} that matches the instance zone {} is {}",
                        args);
                break;
            }
            zoneIndex++;
        }
        // Swap the entries so that you get the instance zone first and then
        // followed by the list of other zones
        // in a circular order
        for (int i = 0; i < zoneIndex; i++) {
            String zone = zones.remove(0);
            zones.add(zone);
        }
        // Now get the eureka urls for all the zones in the order and return it
        List<String> serviceUrls = new ArrayList<String>();
        for (String zone : zones) {
            for (String zoneCname : zoneDnsNamesMap.get(zone)) {
                for (String ec2Url : getEC2DiscoveryUrlsFromZone(zoneCname,
                        DiscoveryUrlType.CNAME)) {
                    String serviceUrl = "http://" + ec2Url + ":"
                            + clientConfig.getEurekaServerPort()

                            + "/" + clientConfig.getEurekaServerURLContext()
                            + "/";
                    logger.debug("The EC2 url is {}", serviceUrl);
                    serviceUrls.add(serviceUrl);
                }
            }
        }
        t.stop();
        return serviceUrls;
    }

    public List<String> getDiscoveryServiceUrls(String zone) {
        boolean shouldUseDns = clientConfig.shouldUseDnsForFetchingServiceUrls();
        if (shouldUseDns) {
            return getServiceUrlsFromDNS(zone,
                    clientConfig.shouldPreferSameZoneEureka());
        }
        return DiscoveryClient.getEurekaServiceUrlsFromConfig(zone,
                                                              clientConfig.shouldPreferSameZoneEureka());
    }

    public enum DiscoveryUrlType {
        CNAME, A
    }

    /**
     * Get the zone that a particular instance is in.
     * 
     * @param myInfo
     *            - The InstanceInfo object of the instance.
     * @return - The zone in which the particular instance belongs to.
     */
    public static String getZone(InstanceInfo myInfo) {
        String availZones[] = clientConfig.getAvailabilityZones(clientConfig
                .getRegion());
        String instanceZone = ((availZones == null || availZones.length == 0) ? "default"
                : availZones[0]);
        if (myInfo != null
                && myInfo.getDataCenterInfo().getName() == Name.Amazon) {

            String awsInstanceZone = ((AmazonInfo) myInfo.getDataCenterInfo())
            .get(MetaDataKey.availabilityZone);
            if (awsInstanceZone != null) {
                instanceZone = awsInstanceZone;
            }

        }
        return instanceZone;
    }

    /**
     * Get the region that this particular instance is in.
     * 
     * @return - The region in which the particular instance belongs to.
     */
    public static String getRegion() {
        String region = clientConfig.getRegion();
        if (region == null) {
            region = "default";
        }
        region = region.trim().toLowerCase();
        return region;
    }

    /**
     * Get the zone based CNAMES that are bound to a region.
     * 
     * @param region
     *            - The region for which the zone names need to be retrieved
     * @return - The list of CNAMES from which the zone-related information can
     *         be retrieved
     */
    static Map<String, List<String>> getZoneBasedDiscoveryUrlsFromRegion(
            String region) {
        String discoveryDnsName = null;
        try {
            discoveryDnsName = "txt." + region + "."
                    + clientConfig.getEurekaServerDNSName();

            logger.debug("The region url to be looked up is {} :",
                    discoveryDnsName);
            Set<String> zoneCnamesForRegion = new TreeSet<String>(
                    DiscoveryClient.getCnamesFromDirContext(dirContext,
                            discoveryDnsName));
            Map<String, List<String>> zoneCnameMapForRegion = new TreeMap<String, List<String>>();
            for (String zoneCname : zoneCnamesForRegion) {
                String zone = null;
                if (isEC2Url(zoneCname)) {
                    throw new RuntimeException(
                            "Cannot find the right DNS entry for "
                                    + discoveryDnsName
                                    + ". "
                                    + "Expected mapping of the format <aws_zone>.<domain_name>");
                } else {
                    String[] cnameTokens = zoneCname.split("\\.");
                    zone = cnameTokens[0];
                    logger.debug("The zoneName mapped to region {} is {}",
                            region, zone);
                }
                List<String> zoneCnamesSet = zoneCnameMapForRegion.get(zone);
                if (zoneCnamesSet == null) {
                    zoneCnamesSet = new ArrayList<String>();
                    zoneCnameMapForRegion.put(zone, zoneCnamesSet);
                }
                zoneCnamesSet.add(zoneCname);
            }
            return zoneCnameMapForRegion;
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get cnames bound to the region:"
                    + discoveryDnsName, e);
        }
    }

    private static boolean isEC2Url(String zoneCname) {
        return zoneCname.startsWith("ec2");
    }

    /**
     * Get the list of EC2 URLs given the zone name
     * 
     * @param dnsName
     *            - The dns name of the zone-specific CNAME
     * @param type
     *            - CNAME or EIP that needs to be retrieved
     * @return - The list of EC2 URLs associated with the dns name
     */
    public static Set<String> getEC2DiscoveryUrlsFromZone(String dnsName,
            DiscoveryUrlType type) {
        Set<String> eipsForZone = null;
        try {
            dnsName = "txt." + dnsName;
            logger.debug("The zone url to be looked up is {} :", dnsName);
            Set<String> ec2UrlsForZone = DiscoveryClient
                    .getCnamesFromDirContext(dirContext, dnsName);
            for (String ec2Url : ec2UrlsForZone) {
                logger.debug("The eureka url for the dns name {} is {}",
                        dnsName, ec2Url);
                ec2UrlsForZone.add(ec2Url);
            }
            if (DiscoveryUrlType.CNAME.equals(type)) {
                return ec2UrlsForZone;
            }
            eipsForZone = new TreeSet<String>();
            for (String cname : ec2UrlsForZone) {
                String[] tokens = cname.split("\\.");
                String ec2HostName = tokens[0];
                String[] ips = ec2HostName.split("-");
                StringBuffer eipBuffer = new StringBuffer();
                for (int ipCtr = 1; ipCtr < 5; ipCtr++) {
                    eipBuffer.append(ips[ipCtr]);
                    if (ipCtr < 4) {
                        eipBuffer.append(".");
                    }
                }
                eipsForZone.add(eipBuffer.toString());
            }
            logger.debug("The EIPS for {} is {} :", dnsName, eipsForZone);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get cnames bound to the region:"
                    + dnsName, e);
        }
        return eipsForZone;
    }

    /**
     * Gets the zone to pick up for this instance.
     * 
     */
    private static int getZoneOffset(String myZone, boolean preferSameZone,
            String[] availZones) {
        for (int i = 0; i < availZones.length; i++) {
            if (myZone != null
                    && (availZones[i].equalsIgnoreCase(myZone.trim()) == preferSameZone)) {
                return i;
            }
        }
        logger.warn(
                "DISCOVERY: Could not pick a zone based on preferred zone settings. My zone - {}, preferSameZone- {}. Defaulting to "
                        + availZones[0], myZone, preferSameZone);
        return 0;
    }

    /**
     * Check if the http status code is a success for the given action.
     * 
     */
    private boolean isOk(Action action, int httpStatus) {
        if (httpStatus >= 200 && httpStatus < 300) {
            return true;
        } else if (Action.Renew == action && httpStatus == 404) {
            return true;
        } else if (Action.Refresh_Delta == action
                && (httpStatus == 403 || httpStatus == 404)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the eureka server which this eureka client communicates with.
     * 
     * @return - The instance information that describes the eureka server.
     */
    private InstanceInfo getCoordinatingServer() {
        Application app = getApplication(DISCOVERY_APPID);
        List<InstanceInfo> discoveryInstances = null;
        InstanceInfo instanceToReturn = null;

        if (app != null) {
            discoveryInstances = app.getInstances();
        }

        if (discoveryInstances != null) {
            for (InstanceInfo instance : discoveryInstances) {
                if ((instance != null)
                        && (instance.isCoordinatingDiscoveryServer())) {
                    instanceToReturn = instance;
                    break;
                }
            }
        }
        return instanceToReturn;
    }

    private ClientResponse getUrl(String fullServiceUrl) {
        ClientResponse cr = discoveryApacheClient.resource(fullServiceUrl)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get(ClientResponse.class);

        return cr;
    }

    /**
     * The heartbeat task that renews the lease in the given intervals.
     * 
     */
    private class HeartbeatThread extends TimerTask {

        public void run() {
            ClientResponse response = null;
            try {
                response = makeRemoteCall(Action.Renew);
                logger.debug(PREFIX
                        + appPathIdentifier
                        + " - Heartbeat status: "
                        + (response != null ? response.getStatus() : "not sent"));
                if (response == null) {
                    return;
                }
                if (response.getStatus() == 404) {
                    REREGISTER_COUNTER.increment();
                    logger.info(PREFIX + appPathIdentifier
                            + " - Re-registering " + "apps/"
                            + instanceInfo.getAppName());
                    register();
                }
            } catch (Throwable e) {
                logger.error(PREFIX + appPathIdentifier
                        + " - was unable to send heartbeat!", e);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
    }

    /**
     * The instance info replicator thread that replicates instance info data to
     * the eureka server at specified intervals.
     * 
     */
    private class InstanceInfoReplicator extends TimerTask {

        public void run() {
            try {
                // TODO: Move the below code to use the InstanceInfoListener
                // Refresh the amazon info including public IP if it has changed
                ApplicationInfoManager.getInstance()
                        .refreshDataCenterInfoIfRequired();
                // Get the co-ordinating Discovery Server
                InstanceInfo discoveryServer = getCoordinatingServer();
                // Check if the ami id has changed. If it has then it means
                // there is a new eureka server deployment now
                // Pass in the appinfo again since
                if ((discoveryServer != null)
                        && (Name.Amazon.equals(discoveryServer
                                .getDataCenterInfo()))) {
                    String amiId = ((AmazonInfo) discoveryServer
                            .getDataCenterInfo()).get(MetaDataKey.amiId);
                    if (discoveryServerAMIId == null) {
                        discoveryServerAMIId = amiId;
                    } else if (!discoveryServerAMIId.equals(amiId)) {
                        logger.info("The eureka AMI ID changed from "
                                + discoveryServerAMIId + " to " + amiId
                                + ". Pushing the appinfo to eureka");
                        // Dirty the app info so that it can be sent
                        instanceInfo.setIsDirty(true);
                        // Assign the new ami id since we have already taken
                        // action
                        discoveryServerAMIId = amiId;
                    }
                }

                if (isHealthCheckEnabled()) {
                    boolean isHealthy = healthCheckCallback.isHealthy();
                    instanceInfo
                            .setStatus(isHealthy ? InstanceInfo.InstanceStatus.UP
                                    : InstanceInfo.InstanceStatus.DOWN);
                }

                if (instanceInfo.isDirty()) {
                    logger.info(PREFIX + appPathIdentifier
                            + " - retransmit instance info with status "
                            + instanceInfo.getStatus().toString());
                    // Simply register again
                    register();
                    instanceInfo.setIsDirty(false);
                }
            } catch (Throwable t) {
                logger.error(
                        "There was a problem with the instance info replicator :",
                        t);
            }
        }

    }

    /**
     * Checks if a {@link HealthCheckCallback} is registered.
     * 
     */
    private boolean isHealthCheckEnabled() {
        return (healthCheckCallback != null && (InstanceInfo.InstanceStatus.STARTING != instanceInfo
                .getStatus() && InstanceInfo.InstanceStatus.OUT_OF_SERVICE != instanceInfo
                .getStatus()));
    }

    /**
     * 
     * The task that fetches the registry information at specified intervals.
     * 
     */
    class CacheRefreshThread extends TimerTask {
        public void run() {
            try {
                boolean remoteRegionsModified = false;
                // This makes sure that a dynamic change to remote regions to fetch is honored.
                String latestRemoteRegions = clientConfig.fetchRegistryForRemoteRegions();
                if (null != latestRemoteRegions) {
                    String currentRemoteRegions = remoteRegionsToFetch.get();
                    if (!latestRemoteRegions.equals(currentRemoteRegions)) {
                        if (remoteRegionsToFetch.compareAndSet(currentRemoteRegions, latestRemoteRegions)) {
                            String[] remoteRegions = latestRemoteRegions.split(",");
                            instanceRegionChecker.getAzToRegionMapper().setRegionsToFetch(remoteRegions);
                            remoteRegionsModified = true;
                        } else {
                            logger.info("Remote regions to fetch modified concurrently, ignoring change from {} to {}",
                                        currentRemoteRegions, latestRemoteRegions);
                        }
                    } else {
                        instanceRegionChecker.getAzToRegionMapper().refreshMapping(); // Just refresh mapping to reflect any DNS/Property change
                    }
                }
                fetchRegistry(remoteRegionsModified);
                if (logger.isInfoEnabled()) {
                    StringBuilder allAppsHashCodes = new StringBuilder();
                    allAppsHashCodes.append("Local region apps hashcode: ");
                    allAppsHashCodes.append(localRegionApps.get().getAppsHashCode());
                    allAppsHashCodes.append(", is fetching remote regions? ");
                    allAppsHashCodes.append(isFetchingRemoteRegionRegistries());
                    for (Map.Entry<String, Applications> entry : remoteRegionVsApps.entrySet()) {
                        allAppsHashCodes.append(", Remote region: ");
                        allAppsHashCodes.append(entry.getKey());
                        allAppsHashCodes.append(" , apps hashcode: ");
                        allAppsHashCodes.append(entry.getValue().getAppsHashCode());
                    }
                    logger.info("Completed cache refresh task for discovery. All Apps hash code is {} ",
                                allAppsHashCodes.toString());
                }
            } catch (Throwable th) {
                logger.error("Cannot fetch registry from server", th);
            }
        }
    }

    /**
     * Load up the DNS JNDI context provider.
     * 
     */
    private static DirContext getDirContext() {
        java.util.Hashtable<String, String> env = new java.util.Hashtable<String, String>();
        env.put(JAVA_NAMING_FACTORY_INITIAL, DNS_NAMING_FACTORY);
        env.put(JAVA_NAMING_PROVIDER_URL, DNS_PROVIDER_URL);

        DirContext dirContext = null;

        try {
            dirContext = new javax.naming.directory.InitialDirContext(env);
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Cannot get dir context for some reason", e);
        }
        return dirContext;
    }

    /**
     * Looks up the DNS name provided in the JNDI context.
     * 
     */
    private static Set<String> getCnamesFromDirContext(DirContext dirContext,
            String discoveryDnsName) throws Throwable {
        javax.naming.directory.Attributes attrs = dirContext.getAttributes(
                discoveryDnsName, new String[] { DNS_RECORD_TYPE });
        javax.naming.directory.Attribute attr = attrs.get(DNS_RECORD_TYPE);
        String txtRecord = null;
        if (attr != null) {
            txtRecord = attr.get().toString();
        }

        Set<String> cnamesSet = new TreeSet<String>();
        if ((txtRecord == null) || ("".equals(txtRecord.trim()))) {
            return cnamesSet;
        }
        String[] cnames = txtRecord.split(" ");
        for (String cname : cnames) {
            cnamesSet.add(cname);
        }
        return cnamesSet;
    }

    private static String[] getInstanceVipAddresses(InstanceInfo instanceInfo,
            boolean isSecure) {
        String vipAddresses;

        if (isSecure) {
            vipAddresses = instanceInfo.getSecureVipAddress();
        } else {
            vipAddresses = instanceInfo.getVIPAddress();
        }

        if (vipAddresses == null) {
            return new String[0];
        }

        return vipAddresses.split(COMMA_STRING);
    }

    /**
     * Fetch the registry information from back up registry if all eureka server
     * urls are unreachable.
     */
    private void fetchRegistryFromBackup() {
        String backupRegistry = clientConfig.getBackupRegistryImpl();
        if (backupRegistry != null) {
            try {
                BackupRegistry backupRegistryInstance = ((BackupRegistry) Class
                        .forName(backupRegistry).newInstance());
                Applications apps = backupRegistryInstance.fetchRegistry();
                if (apps != null) {
                    localRegionApps.set(this.filterAndShuffle(apps));
                    logTotalInstances();
                    logger.info("Fetched registry successfully from the backup");
                }
            } catch (Throwable e) {
                logger.warn(
                        "Cannot fetch applications from apps although backup registry was specified",
                        e);
            }
        }
    }

    /**
     * Gets the task that is responsible for fetching the eureka service Urls.
     * 
     * @param zone
     *            the zone in which the instance resides.
     * @return TimerTask the task which executes periodically.
     */
    private TimerTask getServiceUrlUpdateTask(final String zone) {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    List<String> serviceUrlList = getDiscoveryServiceUrls(zone);
                    if (serviceUrlList.isEmpty()) {
                        logger.warn("The service url list is empty");
                        return;
                    }
                    if (!serviceUrlList.equals(eurekaServiceUrls.get())) {
                        logger.debug(
                                "Updating the serviceUrls as they seem to have changed from {} to {} ",
                                Arrays.toString(eurekaServiceUrls.get()
                                        .toArray()), Arrays
                                        .toString(serviceUrlList.toArray()));

                        eurekaServiceUrls.set(serviceUrlList);
                    }
                } catch (Throwable e) {
                    logger.error("Cannot get the eureka service urls :", e);
                }

            }
        };
    }

    /**
     * Gets the <em>applications</em> after filtering the applications for
     * instances with only UP states and shuffling them.
     * 
     * <p>
     * The filtering depends on the option specified by the configuration
     * {@link EurekaClientConfig#shouldFilterOnlyUpInstances()}. Shuffling helps
     * in randomizing the applications list there by avoiding the same instances
     * receiving traffic during start ups.
     * </p>
     * 
     * @param apps
     *            The applications that needs to be filtered and shuffled.
     * @return The applications after the filter and the shuffle.
     */
    private Applications filterAndShuffle(Applications apps) {
        if (apps != null) {
            if (isFetchingRemoteRegionRegistries()) {
                Map<String, Applications> remoteRegionVsApps = new ConcurrentHashMap<String, Applications>();
                apps.shuffleAndIndexInstances(remoteRegionVsApps, clientConfig, instanceRegionChecker);
                for (Applications applications : remoteRegionVsApps.values()) {
                    applications.shuffleInstances(clientConfig.shouldFilterOnlyUpInstances());
                }
                this.remoteRegionVsApps = remoteRegionVsApps;
            } else {
                apps.shuffleInstances(clientConfig.shouldFilterOnlyUpInstances());
            }
        }
        return apps;
    }

    private boolean isFetchingRemoteRegionRegistries() {
        return null != remoteRegionsToFetch.get();
    }

}
