package com.netflix.discovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.directory.DirContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.DiscoveryJerseyClient;
import com.netflix.discovery.shared.DiscoveryJerseyClient.JerseyClient;
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

public class DiscoveryClient implements LookupService{
    // Tracer name constants
    private final static String PREFIX = "DiscoveryClient: ";
    private static final com.netflix.servo.monitor.Timer GET_SERVICE_URLS_DNS_TIMER = Monitors.newTimer("DiscoveryClient:GetServiceUrlsFromDNS");
    private static final com.netflix.servo.monitor.Timer REGISTER_TIMER = Monitors.newTimer(PREFIX + "Register");;
    private static final com.netflix.servo.monitor.Timer REFRESH_TIMER = Monitors.newTimer( PREFIX + "Refresh");
    private static final com.netflix.servo.monitor.Timer REFRESH_DELTA_TIMER = Monitors.newTimer(PREFIX + "RefreshDelta");
    private static final com.netflix.servo.monitor.Timer RENEW_TIMER = Monitors.newTimer(PREFIX + "Renew");
    private static final com.netflix.servo.monitor.Timer CANCEL_TIMER = Monitors.newTimer(PREFIX + "Cancel");
    private static final com.netflix.servo.monitor.Timer FETCH_REGISTRY_TIMER = Monitors.newTimer(PREFIX + "FetchRegistry");
    private static final Counter SERVER_RETRY_COUNTER = Monitors.newCounter(PREFIX + "Retry");
    private static final Counter ALL_SERVER_FAILURE_COUNT = Monitors.newCounter(PREFIX + "Failed");
    private static final Counter REREGISTER_COUNTER = Monitors.newCounter(PREFIX + "Reregister");

    // Constant strings
    private static final String DNS_PROVIDER_URL = "dns:";
    private static final String DNS_NAMING_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final String JAVA_NAMING_FACTORY_INITIAL = "java.naming.factory.initial";
    private static final String JAVA_NAMING_PROVIDER_URL = "java.naming.provider.url";
    private static final String DNS_RECORD_TYPE = "TXT";
    private static final String VALUE_DELIMITER = ",";
    private static final String COMMA_STRING = VALUE_DELIMITER;
    private static final String DISCOVERY_APPID = "DISCOVERY";
    private static final String UNKNOWN = "UNKNOWN";

    // Defaults
    private static final int GET_REGISTRY_INTERVAL = DynamicPropertyFactory.getInstance().getIntProperty(
            "netflix.discovery.client.refresh.interval", 30000).get();
    private static final int INSTANCEINFO_REPLICATION_INTERVAL = DynamicPropertyFactory.getInstance().getIntProperty("netflix.discovery.appinfo.replicate.interval", 30000).get();
   private static final String PROXY_HOST_CONF = "netflix.discovery.restclient.proxyHost";
    private static final String PROXY_PORT_CONF = "netflix.discovery.restclient.proxyPort";
    private static final String PROP_DISCOVERY_CONTEXT = "netflix.discovery.context";
    private static final String PROP_DISCOVERY_PORT = "netflix.discovery.port";
    private static final String PROP_DISCOVERY_DOMAIN_NAME = "netflix.discovery.domainName";
    private static final DynamicIntProperty SERVICE_URL_GET_INTERVAL_MS = DynamicPropertyFactory.getInstance().getIntProperty("netflix.discovery.dnsPollIntervalMs",5*60*1000);
    private static final String PROP_SHOULD_USE_DNS = "netflix.discovery.shouldUseDns";
    private static final DynamicIntProperty PROP_DIFF_ALLOWED_IN_DELTA_VERSIONS = DynamicPropertyFactory.getInstance().getIntProperty(
            "netflix.discovery.diffAllowedInDeltaVersions", 4);
    private static final String PROP_SERVICE_URL_PREFIX = "netflix.discovery.serviceUrl.";
    private static final String PROP_CLIENT_REGISTRATION_ENABLED = "netflix.discovery.registration.enabled";
   private static final com.netflix.config.DynamicBooleanProperty PREFER_SAME_ZONE = DynamicPropertyFactory.getInstance().getBooleanProperty("netflix.discovery.preferSameZone", true);

    private enum Action {
        Register, Cancel, Renew, Refresh, Refresh_Delta
    }

    private AtomicReference<List<String>> _serviceUrls = new AtomicReference<List<String>>();

    private Timer _scheduledCacheRefreshExecutor = new Timer(
            "Discovery-CacheRefresher", true);

    private Timer _scheduledHeartbeatExecutor = new Timer(
            "Discovery-Heartbeat", true);
    private Timer timer = new Timer("Discovery-ServiceURLUpdater", true);
    
    private Timer _instanceInfoReplicator = new Timer("InstanceInfo-Replictor",
            true);

    private volatile HealthCheckCallback _healthCheckCallback;

    // in-memory cache of the apps and respective instances, refreshed every
    // CACHE_REFRESH_RATE secs
    private volatile AtomicReference<Applications> _apps = new AtomicReference<Applications>();
    private InstanceInfo _myInfo;
    private String _appAndIdPath;
    private boolean _isRegisteredWithDiscovery = false;
    private String _discoveryAMIId;

    private static final DynamicBooleanProperty PRINT_DIFF_CLIENT_SERVER = DynamicPropertyFactory.getInstance().getBooleanProperty(
            "netflix.discovery.printDeltaFullDiff", false);

    private static final Logger s_logger = LoggerFactory.getLogger(DiscoveryClient.class);

    private static final DynamicBooleanProperty DISABLE_DELTA_PROPERTY = DynamicPropertyFactory.getInstance().getBooleanProperty(
            "netflix.discovery.disableDelta", true);
    private static DirContext dirContext = DiscoveryClient.getDirContext();
    
    private JerseyClient discoveryJerseyClient;

    private ApacheHttpClient4 _client;
    /**
     * Should get {@link LookupService} through
     * {@link DiscoveryManager#getLookupService()}
     */
    DiscoveryClient(InstanceInfo myInfo) {
        try {

            final String zone = getZone(myInfo);
            
            _serviceUrls.set(getDiscoveryServiceUrls(zone));
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        List<String> serviceUrlList = getDiscoveryServiceUrls(zone);
                        if (serviceUrlList.isEmpty()) {
                            s_logger.warn("The service url list is empty");
                            return;
                        }
                        if (!serviceUrlList.equals(_serviceUrls.get())) {
                            s_logger.debug(
                                    "Updating the serviceUrls as they seem to have changed from {} to {} ",
                                    _serviceUrls.get(), serviceUrlList);

                            _serviceUrls.set(serviceUrlList);
                        }
                    } catch (Throwable e) {
                        s_logger.error(
                                "Cannot get the discovery service urls :", e);
                    }

                }
            }, SERVICE_URL_GET_INTERVAL_MS.get(), SERVICE_URL_GET_INTERVAL_MS.get());
            _apps.set(new Applications());

            if (myInfo != null) {
                _myInfo = myInfo;
                _appAndIdPath = _myInfo.getAppName() + "/" + _myInfo.getId();
            }

            String proxyHost = DynamicPropertyFactory.getInstance().getStringProperty(PROXY_HOST_CONF, null).get();
            String proxyPort = DynamicPropertyFactory.getInstance().getStringProperty(PROXY_PORT_CONF, null).get();
            int connectTimeout = DynamicPropertyFactory.getInstance().getIntProperty("netflix.discovery.restclient.niws.client.ConnectTimeout", 5000).get();
            int readTimeout = DynamicPropertyFactory.getInstance().getIntProperty("netflix.discovery.restclient.niws.client.ReadTimeout", 8000).get();
            
            discoveryJerseyClient = DiscoveryJerseyClient.createJerseyClient(connectTimeout, readTimeout, 50, 200);
            //discoveryJerseyClient = DiscoveryJerseyClient.createJerseyClient(5000, 8000, 1, 1, 1);
            
            _client = discoveryJerseyClient.getClient();
            ClientConfig cc = discoveryJerseyClient.getClientconfig();
           
            boolean enableGZIPContentEncodingFilter = DynamicPropertyFactory.getInstance().getBooleanProperty("netflix.discovery.restclient.niws.client.ReadTimeout", false).get();
         // should we enable GZip decoding of responses based on Response Headers?
            if (enableGZIPContentEncodingFilter){
                // compressed only if there exists a 'Content-Encoding' header whose value is "gzip"
                _client.addFilter(new GZIPContentEncodingFilter(false));
            }
            if (proxyHost != null && proxyPort != null) {
                cc.getProperties().put(DefaultApacheHttpClient4Config.PROPERTY_PROXY_URI, "http://" + proxyHost + ":" + proxyPort);
            }
    
        
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DiscoveryClient!",
                    e);
        }

        initExecutors();
        if (!fetchRegistry()) {
            String backupRegistry = DynamicPropertyFactory.getInstance().getStringProperty("netflix.discovery.backupregistry", null).get();
            if (backupRegistry != null) {
                try {
                    BackupRegistry backupRegistryInstance = ((BackupRegistry)Class.forName(backupRegistry).newInstance());
                    Applications apps = backupRegistryInstance.fetchRegistry();
                    if (apps != null) {
                        _apps.set(apps);
                        logTotalInstances();
                        s_logger.info("Fetched registry successfully from the backup");
                    }
                } catch (Throwable e) {
                    s_logger.warn("Cannot fetch applications from apps although backup registry was specified", e);
                }
            }
        }
    }

   
    /**
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.shared.LookupService#getApplication(T)
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
        return _apps.get();
    }

    

    /**
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.shared.LookupService#getInstancesById(java.lang
     *      .String)
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
     * Register {@link HealthCheckCallback} with the discovery client.
     * 
     * Once registered, the discovery client will invoke the
     * {@link HealthCheckCallback} every 30 secs.
     * 
     * @param callback
     *            -- app specific healthcheck
     */
    public void registerHealthCheckCallback(HealthCheckCallback callback) {
        if (_myInfo == null) {
            s_logger.error("Cannot register a listener for instance info since it is null!");
        }
        if (callback != null) {
            _healthCheckCallback = callback;
        }
    }

    /**
     * Gets the list of instances matching the given VIP Address
     * 
     * @param vipAddress
     *            - The VIP address to match the instances for
     * @param secure
     *            - true if it is a secure vip address, false otherwise
     * @return - The list of {@link InstanceInfo} objects matching the criteria
     */
    public List<InstanceInfo> getInstancesByVipAddress(String vipAddress,
            boolean secure) {
        if (vipAddress == null) {
            throw new IllegalArgumentException(
                    "Supplied VIP Address cannot be null");
        }

        List<InstanceInfo> result = new ArrayList<InstanceInfo>();

        for (Application app : getApplications().getRegisteredApplications()) {
            for (InstanceInfo instance : app.getInstances()) {
                String[] instanceVipAddresses = getInstanceVipAddresses(
                        instance, secure);
                for (String vipAddressFromList : instanceVipAddresses) {
                    if (vipAddress.equalsIgnoreCase(vipAddressFromList.trim())) {
                        result.add(instance);

                        break;
                    }
                }
            }
        }

        return result;
    }

    @VisibleForTesting
    static String[] getInstanceVipAddresses(InstanceInfo instanceInfo,
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
     * Gets the list of instances matching the given VIP Address and the given
     * application name if both of them are not null. If one of them is null,
     * then that criterion is completely ignored for matching instances.
     * 
     * @param vipAddress
     *            - The VIP address to match the instances for.
     * @param appName
     *            - The applicationName to match the instances for
     * @param secure
     *            - true if it is a secure vip address, false otherwise
     * @return - The list of {@link InstanceInfo} objects matching the criteria
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

    /**
     * Get all applications registered with a specific discovery service
     * 
     * @param serviceUrl
     *            - The string representation of the service url
     * @return - The applications
     */
    public Applications getApplications(String serviceUrl) {
        ClientResponse response = null;
        Applications apps = null;
        try {
            response = getUrl(serviceUrl + "apps/");
            apps = response.getEntity(Applications.class);
            s_logger.debug(PREFIX + _appAndIdPath + " -  refresh status: "
                    + response.getStatus());
            return apps;
        } catch (Exception e) {
            s_logger.error(
                    PREFIX + _appAndIdPath
                            + " - was unable to refresh it's cache! status = "
                            + e.getMessage(), e);

        } finally {
            if (response != null) {
                response.close();
            }
        }
        return apps;
    }

    

    public void shutdown() {
        // If APPINFO was registered
        if (_myInfo != null) {
            _myInfo.setStatus(InstanceStatus.DOWN);
            unregister();
        }
    }

    /**
     * Checks to see if the discovery client registration is enabled.
     * 
     * @param myInfo
     *            - The instance info object
     * @return - true, if the instance should be registered with discovery,
     *         false otherwise
     */
    private boolean shouldRegister(InstanceInfo myInfo) {
        boolean shouldRegister = DynamicPropertyFactory.getInstance().getBooleanProperty(PROP_CLIENT_REGISTRATION_ENABLED, true).get();
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
     * Register w/ the discovery service
     */
    void register() {
        s_logger.debug(PREFIX + _appAndIdPath + ": registering service...");
        ClientResponse response = null;
        try {
            response = makeRemoteCall(Action.Register);
            _isRegisteredWithDiscovery = true;
            s_logger.info(PREFIX + _appAndIdPath + " - registration status: "
                    + (response != null ? response.getStatus() : "not sent"));

        } catch (Throwable e) {
            // TODO:
            s_logger.error(PREFIX + _appAndIdPath + " - registration failed"
                    + e.getMessage(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * unregister w/ the discovery service
     */
    void unregister() {
        ClientResponse response = null;
        try {
            response = makeRemoteCall(Action.Cancel);

            s_logger.info(PREFIX
                    + _appAndIdPath
                    + " - deregister  status: "
                    + (response != null ? response.getStatus()
                            : "not registered"));
        } catch (Throwable e) {
            s_logger.error(PREFIX + _appAndIdPath + " - de-registration failed"
                    + e.getMessage(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private boolean fetchRegistry() {
        ClientResponse response = null;
        Stopwatch tracer = FETCH_REGISTRY_TIMER.start();
        
        try {
            // If the delta is disabled or if it is the first time, get all
            // applications
            if (DISABLE_DELTA_PROPERTY.get()
                    || (getApplications() == null)
                    || (getApplications().getRegisteredApplications().size() == 0)
                    // The client application does not have the latest library
                    // supporting delta
                    || (getApplications().getVersion() == -1)) {

                s_logger.info("Disable delta property : {}",
                        DISABLE_DELTA_PROPERTY.get());
                s_logger.info("Application is null : {}",
                        (getApplications() == null));
                s_logger.info(
                        "Registered Applications size is zero : {}",
                        (getApplications().getRegisteredApplications().size() == 0));
                s_logger.info("Application version is -1: {}",
                        (getApplications().getVersion() == -1));

                response = makeRemoteCall(Action.Refresh);
                s_logger.info("Getting all instance registry info from the discovery server");
                Applications apps = response.getEntity(Applications.class);
                _apps.set(apps);
                s_logger.info("The response status is {}",
                        response.getStatus());
            } else {
                Applications delta = null;
                response = makeRemoteCall(Action.Refresh_Delta);
                if (response.getStatus() == Status.OK.getStatusCode()) {
                    delta = response.getEntity(Applications.class);
                }
                if ((delta == null)
                        || (delta.getVersion() - getApplications().getVersion() > PROP_DIFF_ALLOWED_IN_DELTA_VERSIONS
                                .get())
                        // The client might have switched servers, in that case
                        // get the whole set and sync up versions
                        || (getApplications().getVersion() > delta.getVersion())) {

                    s_logger.info("Getting the full list since the criteria for getting delta is not satisfied");
                    this.closeResponse(response);
                    response = makeRemoteCall(Action.Refresh);
                    Applications apps = response.getEntity(Applications.class);
                    _apps.set(apps);
                    if (delta == null) {
                        s_logger.info("The server does not allow the delta revision to be applied because it is not safe. Hence got the full registry.");
                    } else {
                        Object[] args = {delta.getVersion() - getApplications()
                                        .getVersion(), delta.getVersion(),
                                getApplications().getVersion()};
                        s_logger.info(
                                "The delta diff between the server and the client is greater than that is allowed : {}, server version {}, client version {}."
                                        + "Hence got the full registry.",
                                args);
                        getApplications().setVersion(delta.getVersion());
                    }
                } else {
                    s_logger.info(
                            "The delta version of client/server is {}/{} ",
                            getApplications().getVersion(), delta.getVersion());
                    updateDelta(delta);
                    String reconcileHashCode = getApplications()
                            .getReconcileHashCode();
                    // There is a diff in number of instances for some reason
                    if ((!reconcileHashCode.equals(delta.getAppsHashCode()))
                            || PRINT_DIFF_CLIENT_SERVER.get()) {
                        response = reconcileAndLogDifference(response, delta,
                                reconcileHashCode);

                    }
                }
                logTotalInstances();
            }

            s_logger.debug(PREFIX + _appAndIdPath + " -  refresh status: "
                    + response.getStatus());
        } catch (Throwable e) {
            s_logger.error(
                    PREFIX + _appAndIdPath
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


    private void logTotalInstances() {
        int totInstances = 0;
        for (Application application : getApplications()
                .getRegisteredApplications()) {
            totInstances += application.getInstances().size();
        }
        s_logger.info(
                "The total number of instances in the client now is {}",
                totInstances);
    }

    private ClientResponse reconcileAndLogDifference(ClientResponse response,
            Applications delta, String reconcileHashCode) throws Throwable
             {
        s_logger.info(
                "The Reconcile hashcodes do not match, client : {}, server : {}. Getting the full registry",
                reconcileHashCode, delta.getAppsHashCode());
       
        this.closeResponse(response);
        response = makeRemoteCall(Action.Refresh);
        Applications serverApps = (Applications) response
                .getEntity(Applications.class);
        Map<String, List<String>> reconcileDiffMap = getApplications()
                .getReconcileMapDiff(serverApps);
        String reconcileString = "";
        for (Map.Entry<String, List<String>> mapEntry : reconcileDiffMap
                .entrySet()) {
            reconcileString = reconcileString + mapEntry.getKey() + ": ";
            for (String displayString : mapEntry.getValue()) {
                reconcileString = reconcileString + displayString;
            }
            reconcileString = reconcileString + "\n";
        }
        s_logger.info("The reconcile string is {}", reconcileString);
        
        _apps.set(serverApps);
        getApplications().setVersion(delta.getVersion());
        s_logger.info(
                "The Reconcile hashcodes after complete sync up, client : {}, server : {}.",
                getApplications().getReconcileHashCode(),
                delta.getAppsHashCode());
        return response;
    }

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
                    s_logger.info("Added instance {} to the existing apps ",
                            instance.getId());
                    getApplications().getRegisteredApplications(
                            instance.getAppName()).addInstance(instance);
                } else if (ActionType.MODIFIED.equals(instance.getActionType())) {
                    Application existingApp = getApplications()
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        getApplications().addApplication(app);
                    }
                    s_logger.info(
                            "Modified instance {} to the existing apps ",
                            instance.getId());

                    getApplications().getRegisteredApplications(
                            instance.getAppName()).addInstance(instance);

                } else if (ActionType.DELETED.equals(instance.getActionType())) {
                    Application existingApp = getApplications()
                            .getRegisteredApplications(instance.getAppName());
                    if (existingApp == null) {
                        getApplications().addApplication(app);
                    }
                    s_logger.info(
                            "Deleted instance {} to the existing apps ",
                            instance.getId());
                    getApplications().getRegisteredApplications(
                            instance.getAppName()).removeInstance(instance);
                }
            }
        }
        s_logger.info(
                "The total number of instances fetched by the delta processor : {}",
                deltaCount);
        getApplications().setVersion(delta.getVersion());
    }

    private ClientResponse makeRemoteCall(Action action) throws Throwable {
        return makeRemoteCall(action, 0);
    }

    /**
     * If there is an exception, try backup service urls until we exhaust them
     */
    private ClientResponse makeRemoteCall(Action action, int serviceUrlIndex)
            throws Throwable {
        String urlPath = null;
        Stopwatch tracer = null;
        String serviceUrl = _serviceUrls.get().get(serviceUrlIndex);
        ClientResponse response = null;
        s_logger.info("Discovery Client talking to the server {}", serviceUrl);
        try {
            // If the application is unknown do not register/renew/cancel but
            // refresh
            if ((UNKNOWN.equals(_myInfo.getAppName())
                    && (!Action.Refresh.equals(action)) && (!Action.Refresh_Delta
                    .equals(action)))) {
                return null;
            }
            WebResource r = _client.resource(serviceUrl);
            switch (action) {

            case Renew:
                tracer = RENEW_TIMER.start();
                urlPath = "apps/" + _appAndIdPath;
                response = r.path(urlPath).queryParam("status", _myInfo.getStatus().toString()).queryParam("lastDirtyTimestamp", _myInfo.getLastDirtyTimestamp().toString()).put(ClientResponse.class);
                break;
            case Refresh:
                tracer = REFRESH_TIMER.start();
                urlPath = "apps/";
                response = getUrl(serviceUrl + urlPath);
                break;
            case Refresh_Delta:
                tracer =  REFRESH_DELTA_TIMER.start();
                urlPath = "apps/delta";
                response = getUrl(serviceUrl + urlPath);
                break;
            case Register:
                tracer = REGISTER_TIMER.start();
                urlPath = "apps/" + _myInfo.getAppName();
                
                response = r.path(urlPath).type(MediaType.APPLICATION_JSON_TYPE).post(ClientResponse.class, _myInfo);
                
                break;
            case Cancel:
                tracer = CANCEL_TIMER.start();
                urlPath = "apps/" + _appAndIdPath;
                response = r.path(urlPath).delete(ClientResponse.class);
                // Return without during de-registration if it is not registered
                // already and if we get a 404
                if ((!_isRegisteredWithDiscovery)
                        && (response.getStatus() == Status.NOT_FOUND
                                .getStatusCode())) {
                    return response;
                }
                break;
            }

            if (isOk(action, response.getStatus())) {
                return response;
            } else {
                s_logger.warn("Action: " + action + "  => returned status of "
                        + response.getStatus() + " from " + serviceUrl
                        + urlPath);
                throw new RuntimeException("Bad status: "
                        + response.getStatus());
            }
        } catch (Throwable t) {
            closeResponse(response);
            String msg = "Can't get a response from " + serviceUrl + urlPath;
            if (_serviceUrls.get().size() > (++serviceUrlIndex)) {
                s_logger.warn(msg, t);
                s_logger.warn("Trying backup: "
                        + _serviceUrls.get().get(serviceUrlIndex));
                SERVER_RETRY_COUNTER.increment();
                return makeRemoteCall(action, serviceUrlIndex);
            } else {
                ALL_SERVER_FAILURE_COUNT.increment();
                s_logger.error(
                        msg
                                + "\nCan't contact any discovery nodes - possibly a security group issue?",
                        t);
                throw t;
            }
        } finally {
            if (tracer != null) {
                tracer.stop();
            }
        }
    }

    private void closeResponse(ClientResponse response) {
        if (response != null) {
            try {
                response.close();
            } catch (Throwable th) {
                s_logger.error("Cannot release response resource :", th);
            }
        }
    }

    private void initExecutors() {
        // Schedule refreshes
       _scheduledCacheRefreshExecutor.schedule(new CacheRefreshThread(),
                GET_REGISTRY_INTERVAL , GET_REGISTRY_INTERVAL);

        if (shouldRegister(_myInfo)) {
            s_logger.info("Starting heartbeat executor: "
                    + "renew interval is: "
                    + _myInfo.getLeaseInfo().getRenewalIntervalInSecs());

            _scheduledHeartbeatExecutor.schedule(new HeartbeatThread(), _myInfo
                    .getLeaseInfo().getRenewalIntervalInSecs() * 1000, _myInfo
                    .getLeaseInfo().getRenewalIntervalInSecs() * 1000);

            // Schedule refreshes
           _instanceInfoReplicator.schedule(new InstanceInfoReplicator(),
                    (10 * 1000) + INSTANCEINFO_REPLICATION_INTERVAL,
                    INSTANCEINFO_REPLICATION_INTERVAL);
            
        }
    }


    /**
     * Get the list of all ec2 service urls from route 53 for the discovery client to talk to
     * @param instanceZone - The zone in which the client resides
     * @param preferSameZone - true if we have to prefer the same zone as the client, false otherwise
     * @return - The list of all ec2 service urls for the discovery client to talk to
     */
    public static List<String> getServiceUrlsFromDNS(String instanceZone, boolean preferSameZone ) {
        Stopwatch t = GET_SERVICE_URLS_DNS_TIMER.start();
        String region = getRegion();
        // Get zone-specific DNS names for the given region so that we can get a list of available zones
        Map<String, List<String>> zoneDnsNamesMap = getZoneBasedDiscoveryUrlsFromRegion(region);
        Set<String> availableZones = zoneDnsNamesMap.keySet();
        List<String> zones = new LinkedList<String>(availableZones);
        int zoneIndex = 0;
        boolean zoneFound = false;
        for (String zone : zones) {  
          s_logger.debug("Checking if the instance zone {} is the same as the zone from DNS {}", instanceZone, zone);
          if (preferSameZone) {
                if (instanceZone.equalsIgnoreCase(zone)) {
                  zoneFound = true;
               }
            }
            else {
                if (!instanceZone.equalsIgnoreCase(zone)) {
                    zoneFound = true;
                }
            }
          if (zoneFound) {
            Object[] args = {zones, instanceZone, zoneIndex };
            s_logger.debug("The zone index from the list {} that matches the instance zone {} is {}", args);
            break;
          }
         zoneIndex++;
        }
        // Swap the entries so that you get the instance zone first and then followed by the list of other zones
        // in a circular order
        for (int i = 0; i < zoneIndex; i++) {
             String zone = zones.remove(0);
             zones.add(zone);
         }
        // Now get the ec2 urls for all the zones in the order and return it
        List<String> serviceUrls = new ArrayList<String>();
        for (String zone : zones) {
            for (String zoneCname : zoneDnsNamesMap.get(zone)) {
               for (String ec2Url: getEC2DiscoveryUrlsFromZone(zoneCname, DiscoveryUrlType.CNAME)) {
                    String serviceUrl = "http://"
                            + ec2Url
                            + ":"
                            + DynamicPropertyFactory.getInstance()
                                    .getStringProperty(PROP_DISCOVERY_PORT, null)
                                    .get()
                            + "/"
                            + DynamicPropertyFactory.getInstance().getStringProperty(
                                    PROP_DISCOVERY_CONTEXT, null).get()
                            + "/";
        s_logger.debug("The EC2 url is {}", serviceUrl);
                   serviceUrls.add(serviceUrl);
               }
            }
        }
        t.stop();
        return serviceUrls;
    }

    /**
     * Get the list of all ec2 service urls from properties file for the discovery client to talk to
     * @param instanceZone - The zone in which the client resides
     * @param preferSameZone - true if we have to prefer the same zone as the client, false otherwise
     * @return - The list of all ec2 service urls for the discovery client to talk to
     */
    public static List<String> getDiscoveryServiceURLSFromProperties(String instanceZone, boolean preferSameZone) {
        List<String> orderedUrls = new ArrayList<String>();
        
       
        String region = getRegion();

        s_logger.debug("Looking up property : " + "netflix.discovery." + region
                + ".availabilityZones");
        String availZones[] = DynamicPropertyFactory.getInstance().getStringProperty(
                "netflix.discovery." + region + ".availabilityZones", "").get().split(VALUE_DELIMITER);
        s_logger.debug("The availability zone for the given region {} are %s",
                region, Arrays.toString(availZones));
        int myZoneOffset = getZoneOffset(instanceZone, preferSameZone, availZones);

        orderedUrls
                .add(getStringFastProperty(
                        PROP_SERVICE_URL_PREFIX + availZones[myZoneOffset])
                        .get());
        int currentOffset = myZoneOffset == (availZones.length - 1) ? 0
                : (myZoneOffset + 1);
        while (currentOffset != myZoneOffset) {
            orderedUrls.add(getStringFastProperty(
                    PROP_SERVICE_URL_PREFIX + availZones[currentOffset])
                    .get());
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
     * Get the zone based CNAMES that are bound to a region. 
     * @param region - The region for which the zone names need to be retrieved
     * @return - The list of CNAMES from which the zone-related information can be retrieved
     */
    private static Map<String, List<String>> getZoneBasedDiscoveryUrlsFromRegion(
            String region) {
        String discoveryDnsName = null;
        try {
           discoveryDnsName = "txt." + region
                    + "."
                    + DynamicPropertyFactory.getInstance().getStringProperty(PROP_DISCOVERY_DOMAIN_NAME, null).get();
                            
            s_logger.debug("The region url to be looked up is {} :",
                    discoveryDnsName);
            Set<String> zoneCnamesForRegion = new TreeSet<String>(DiscoveryClient.getCnamesFromDirContext(dirContext, discoveryDnsName));
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
                    s_logger.debug("The zoneName mapped to region {} is {}",
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
     * @param dnsName - The dns name of the zone-specific CNAME
     * @param type - CNAME or EIP that needs to be retrieved
     * @return - The list of EC2 URLs associated with the dns name
     */
    public static Set<String> getEC2DiscoveryUrlsFromZone(String dnsName,
            DiscoveryUrlType type) {
        Set<String> eipsForZone = null;
        try {
            dnsName = "txt." + dnsName;
            s_logger.debug("The zone url to be looked up is {} :", dnsName);
            Set<String> ec2UrlsForZone = DiscoveryClient.getCnamesFromDirContext(dirContext, dnsName);
            for (String ec2Url : ec2UrlsForZone) {
                s_logger.debug("The ec2 url for the dns name {} is {}",
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
            s_logger.debug("The EIPS for {} is {} :", dnsName, eipsForZone);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get cnames bound to the region:"
                    + dnsName, e);
        }
        return eipsForZone;
    }

    private static DynamicStringProperty getStringFastProperty(String propName) {
        DynamicStringProperty prop = DynamicPropertyFactory.getInstance().getStringProperty(propName, null);
        if (prop == null || prop.get() == null) {
            throw new IllegalAccessError("Couldn't access required property: "
                    + propName);
        } else {
            return prop;
        }
    }

    /**
     * Return ordered list of possible DS service URLs available, starting w/
     * the serviceUrl in the same zone as the client.
     * 
     * @deprecated
     */
    @Deprecated
    public static String[] getServiceUrls(InstanceInfo myInfo) {
        String availZones[] = DynamicPropertyFactory.getInstance().getStringProperty(
                "netflix.discovery." + getRegion() + ".availabilityZones", "").get().split(VALUE_DELIMITER);
      
        String instanceZone = availZones[0];
        LinkedHashSet<String> orderedUrls = new LinkedHashSet<String>();
        if (myInfo != null
                && myInfo.getDataCenterInfo().getName() == Name.Amazon) {
            instanceZone = ((AmazonInfo) myInfo.getDataCenterInfo())
                    .get(MetaDataKey.availabilityZone);

        }
        int myZoneOffset = getZoneOffset(instanceZone, !myInfo.getAppName()
                .equalsIgnoreCase("DISCOVERY"),
                availZones);
        orderedUrls.addAll(Arrays.asList(DynamicPropertyFactory.getInstance().getStringProperty(PROP_SERVICE_URL_PREFIX + availZones[myZoneOffset], "").get()
                .split(COMMA_STRING)));
        int currentOffset = myZoneOffset == (availZones.length - 1) ? 0
                : (myZoneOffset + 1);
        while (currentOffset != myZoneOffset) {
            orderedUrls.addAll(
                    Arrays.asList(DynamicPropertyFactory.getInstance().getStringProperty(PROP_SERVICE_URL_PREFIX
                            + availZones[currentOffset], "").get()
                            .split(COMMA_STRING)));
                    
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
        String serviceUrls[] = new String[orderedUrls.size()];
        int i = 0;
        for (String url : orderedUrls) {
            serviceUrls[i++] = url;
        }
        return serviceUrls;
    }

    private static int getZoneOffset(String myZone, boolean preferSameZone,
            String[] availZones) {
        for (int i = 0; i < availZones.length; i++) {
            if (myZone != null
                    && (availZones[i].equalsIgnoreCase(myZone.trim()) == preferSameZone)) {
                return i;
            }
        }
        s_logger.error("DISCOVERY: invalid zone - " + myZone
                + " defaulting to " + availZones[0]);
        return 0;
    }

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
     * Returns the discovery server which this discovery client communicates
     * with
     * 
     * @return - The instance info the the discovery server
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

    private ClientResponse getUrl(String fullServiceUrl)
            {
        ClientResponse cr =  _client.resource(fullServiceUrl).accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);
        
        return cr;
    }

    class HeartbeatThread extends TimerTask {

     
        public void run() {
            ClientResponse response = null;
            try {
                response = makeRemoteCall(Action.Renew);
                s_logger.debug(PREFIX
                        + _appAndIdPath
                        + " - Heartbeat status: "
                        + (response != null ? response.getStatus() : "not sent"));
                if (response == null) {
                    return;
                }
                if (response.getStatus() == 404) {
                    REREGISTER_COUNTER.increment();
                    s_logger.info(PREFIX + _appAndIdPath + " - Re-registering "
                            + "apps/" + _myInfo.getAppName());
                    register();
                }
            } catch (Throwable e) {
                s_logger.error(PREFIX + _appAndIdPath
                        + " - was unable to send heartbeat!", e);
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
    }

    private class InstanceInfoReplicator extends TimerTask {

        public void run() {
            try {
                // TODO: Move the below code to use the InstanceInfoListener
                // Refresh the amazon info including public IP if it has changed
                _myInfo.refresh();
                // Get the co-ordinating Discovery Server
                InstanceInfo discoveryServer = getCoordinatingServer();
                // Check if the ami id has changed. If it has then it means
                // there is a new discovery server deployment now
                // Pass in the appinfo again since
                if ((discoveryServer != null)
                        && (Name.Amazon.equals(discoveryServer
                                .getDataCenterInfo()))) {
                    String amiId = ((AmazonInfo) discoveryServer
                            .getDataCenterInfo()).get(MetaDataKey.amiId);
                    if (_discoveryAMIId == null) {
                        _discoveryAMIId = amiId;
                    } else if (!_discoveryAMIId.equals(amiId)) {
                        s_logger.info("The discovery AMI ID changed from "
                                + _discoveryAMIId + " to " + amiId
                                + ". Pushing the appinfo to discovery");
                        // Dirty the app info so that it can be sent
                        _myInfo.setIsDirty(true);
                        // Assign the new ami id since we have already taken
                        // action
                        _discoveryAMIId = amiId;
                    }
                }

                if (isHealthCheckEnabled()) {
                    boolean isHealthy = _healthCheckCallback.isHealthy();
                    _myInfo.setStatus(isHealthy ? InstanceInfo.InstanceStatus.UP
                            : InstanceInfo.InstanceStatus.DOWN);
                }

                if (_myInfo.isDirty()) {
                    s_logger.info(PREFIX + _appAndIdPath
                            + " - retransmit instance info with status "
                            + _myInfo.getStatus().toString());
                    // Simply register again
                    register();
                    _myInfo.setIsDirty(false);
                }
            } catch (Throwable t) {
                s_logger.error(
                        "There was a problem with the instance info replicator :",
                        t);
            }
        }

    }

    private boolean isHealthCheckEnabled() {
        return (_healthCheckCallback != null && (InstanceInfo.InstanceStatus.STARTING != _myInfo
                .getStatus() && InstanceInfo.InstanceStatus.OUT_OF_SERVICE != _myInfo
                .getStatus()));
    }

    class CacheRefreshThread extends TimerTask {
        public void run() {
            try {
                fetchRegistry();
            } catch (Throwable th) {
                s_logger.error("Cannot fetch registry from server", th);
            }
        }
    }
 
    /**
     * Get the zone that a particular instance is in
     * @param myInfo - The InstanceInfo object of the instance
     * @return - The zone in which the particular instance belongs to
     */
    public static String getZone(InstanceInfo myInfo) {
        String availZones[] = DynamicPropertyFactory.getInstance().getStringProperty(
                "netflix.discovery." + getRegion() + ".availabilityZones", "").get().split(VALUE_DELIMITER);
        String instanceZone = availZones[0];
            if (myInfo != null
                    && myInfo.getDataCenterInfo().getName() == Name.Amazon) {
                instanceZone = ((AmazonInfo) myInfo.getDataCenterInfo())
                        .get(MetaDataKey.availabilityZone);
            }
        if (instanceZone == null) {
            instanceZone = availZones[0];
            s_logger.warn("The instance zone is null, so settting it to {}", instanceZone);
        }
        return instanceZone;
    }
    
    /**
     * Get the region that this particular instance is in
     * @return - The region in which the particular instance belongs to
     */
    public static String getRegion() {
        String region = ConfigurationManager.getDeploymentContext().getDeploymentRegion();
        region = region.trim().toLowerCase();
        return region;
    }
    
   
    private static DirContext getDirContext() {
        java.util.Hashtable<String, String> env = new java.util.Hashtable<String, String>();
        env.put(JAVA_NAMING_FACTORY_INITIAL, DNS_NAMING_FACTORY);
        env.put(JAVA_NAMING_PROVIDER_URL,DNS_PROVIDER_URL);
        
        DirContext dirContext = null;

        try {
            dirContext = new javax.naming.directory.InitialDirContext(env);
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Cannot get dir context for some reason", e);
        }
        return dirContext;
    }
    
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
    
   
    
    public static List<String> getDiscoveryServiceUrls(String zone) {
        boolean shouldUseDns = DynamicPropertyFactory.getInstance().getBooleanProperty(PROP_SHOULD_USE_DNS, true).get();
        if (shouldUseDns) {
            return DiscoveryClient.getServiceUrlsFromDNS(zone, PREFER_SAME_ZONE.get());
        }
        return DiscoveryClient.getDiscoveryServiceURLSFromProperties(zone, PREFER_SAME_ZONE.get());
    }
    
    public enum DiscoveryUrlType {
        CNAME, A
    }
    
    
}
