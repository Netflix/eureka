/*
 * ApplicationInfoManager.java
 *
 * $Header: //depot/commonlibraries/eureka-client/main/src/com/netfix/appinfo/ApplicationInfoManager.java#1 $
 * $DateTime: 2012/07/16 11:58:15 $
 *
 * Copyright (c) 2008 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.ws.rs.core.Response;

import org.apache.commons.configuration.ConfigurationException;

import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.config.FastProperty.BooleanProperty;
import com.netflix.config.FastProperty.IntProperty;
import com.netflix.config.NetflixConfiguration;
import com.netflix.config.NetflixConfiguration.DatacenterEnum;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.shared.Application;
import com.netflix.logging.ILog;
import com.netflix.logging.LogManager;
import com.netflix.monitoring.MonitorRegistry;
import com.netflix.monitoring.MonitorRegistry.Namespace;
import com.netflix.niws.Constants;
import com.netflix.niws.client.IClientResponse;
import com.netflix.niws.client.NFMultivaluedMap;
import com.netflix.niws.client.NiwsClientConfig;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;
import com.netflix.niws.client.RestClient;
import com.netflix.niws.client.RestClientFactory;
import com.netflix.platform.components.Callback;
import com.netflix.platform.components.ComponentException;
import com.netflix.platform.components.IComponent;
import com.netflix.util.Pair;

/**
 * Standard way to access basic application information.
 * This information will be used by our monitoring and/or discovery
 * services.
 *
 * {@link InstanceInfo} is populated once during platform
 * initialization so it is pretty much static data after the platform
 * is fully initialized.
 *
 * Application info can be specified in two ways:
 * <ul>
 * <li>Through the existing application override configuration. Typically
 *  this should be set by the our deployment tool (e.g. WebCM)
 * <p>
 * Example:
 * <pre>{@code
 *      netflix.appinfo.name=www # Please read http://wiki.netflix.com/clearspace/docs/DOC-16287
 *      netflix.appinfo.sid=123
 *      netflix.appinfo.version=v1
 *      netflix.appinfo.port=7001
 *      netflix.appinfo.dependencies=ST_DB,WCS,WNS,ESB,WSS
 *      netflix.appinfo.metadata.name1=value2
 *      netflix.appinfo.metadata.name2=value3
 *      ...
 * }</pre>
 * <li>Or via a callback mechanism by registering your own
 *  class implementing
 *  <pre>{@code Callback<ApplicationInfo>}</pre>
 *  you'll need to set the fully qualified class in the app override configuration (
 *  {@code netflix.appinfo.callback=com.netflix.appX.Callback}
 * </ul>
 *
 * @author gkim
 */
public class ApplicationInfoManager implements IComponent {

    private static final String PROP_ASG_NAME = "NETFLIX_AUTO_SCALE_GROUP";
    public static final String UNKNOWN_APPLICATION          = "unknown";
    static final String DEFAULT_HEALTHCHECK_URLPATH         = "/healthcheck";
	public static final String NAME                         = "APPINFO";
    public static final String CONFIG_NAME                  = "platform:appinfo";

    public static final String PROP_DATACENTER              = "netflix.datacenter";
    public static final String NAMESPACE                    = "netflix.appinfo.";
    public static final String METADATA_NAMESPACE           = "netflix.appinfo.metadata.";
    public static final String PROP_NAME                    = NAMESPACE + "name";
    public static final String PROP_DEPS                    = NAMESPACE + "dependencies";
    public static final String PROP_SERVETRAFFIC_ATSTARTUP  = NAMESPACE + "traffic.enabled";
    public static final String PROP_PORT                    = NAMESPACE +"port";
    public static final String PROP_PORT_ENABLED            = PROP_PORT  + ".enabled";
    public static final String PROP_SECURE_PORT             = NAMESPACE + "securePort";
    public static final String PROP_SECURE_PORT_ENABLED     = PROP_SECURE_PORT + ".enabled";
    public static final String PROP_COUNTRY_ID              = NAMESPACE + "countryId";
    public static final String PROP_VERSION                 = NAMESPACE + "version";
    public static final String PROP_SOURCE_VERSION          = NAMESPACE +"sourceVersion";
    public static final String PROP_BUILD_DATE              = NAMESPACE + "buildDate";
    public static final String PROP_CALLBACK                = NAMESPACE + "callback";
    public static final String PROP_SID                     = NAMESPACE + "sid";
    public static final String PROP_LEASE_RENEW_INTERVAL    = NAMESPACE + "lease.renewalInterval";
    public static final String PROP_LEASE_DURATION          = NAMESPACE + "lease.duration";

    static final String PROP_HOMEPAGE_URLPATH               = NAMESPACE + "homePageUrlPath";
    static final String PROP_HOMEPAGE_URL                   = NAMESPACE + "homePageUrl";
    static final String PROP_STATUSPAGE_URLPATH             = NAMESPACE + "statusPageUrlPath";
    static final String PROP_STATUSPAGE_URL                 = NAMESPACE + "statusPageUrl";
    static final String PROP_HEALTHCHECK_URLPATH            = NAMESPACE + "healthCheckUrlPath";
    static final String PROP_HEALTHCHECK_URL                = NAMESPACE + "healthCheckUrl";
    static final String PROP_SECURE_HEALTHCHECK_URL         = NAMESPACE + "secureHealthCheckUrl";
    static final String PROP_SECURE_VIP_ADDRESS             = NAMESPACE + "secureVipAddress";
    static final String PROP_VIP_ADDRESS                    = NAMESPACE + "vipAddress";
    static final String DEFAULT_STATUSPAGE_URLPATH          = "/Status";
    static final String DEFAULT_HOMEPAGE_URLPATH             = "/";

    private static final String PROP_ACCEPTED_VERSIONS      = NAMESPACE  + "acceptedVersions";

    //TODO: probably should move this out to another component
    // These props are currently only applicable in the cloud
    private static final IntProperty FAST_PROP_JMX_PORT         = new IntProperty("netflix.jmx.port", 7500);
    private static final BooleanProperty FAST_PROP_JMX_ENABLED  = new BooleanProperty("netflix.jmx.enabled", true);
    private static final BooleanProperty PROP_VALIDATE_INSTANCE_ID = new BooleanProperty("netflix.appinfo.validateInstanceId", true);

    private static final ApplicationInfoManager s_instance = new ApplicationInfoManager();

    private Status _status = Status.UNTOUCHED;
    private InstanceInfo _info;
    private Callback<InstanceInfo.Builder> _callback;

    private static final ILog s_logger = LogManager.getLogger(ApplicationInfoManager.class);

    private ApplicationInfoManager() { }

    public static ApplicationInfoManager getInstance() {
        return s_instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Properties getProperties() {
        try{
            return NetflixConfiguration.getProperties(
                    NetflixConfiguration.getInstance().getNamedConfiguration(CONFIG_NAME));
        }catch(ConfigurationException e) {
            s_logger.error(e);
        }catch(FileNotFoundException e){
            s_logger.error(e);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Status getStatus() {
        return _status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void initComponent() throws ComponentException{
        _status = Status.INITIALIZING;
        try {
            NetflixConfiguration config = NetflixConfiguration.getConfigInstance();

            String callbackClsName = config.getString(PROP_CALLBACK);

            if(callbackClsName != null && !"".equals(callbackClsName)){
                Class<Callback<InstanceInfo.Builder>> klass =
                    (Class<Callback<InstanceInfo.Builder>>)Class.forName(callbackClsName);

                _callback = klass.newInstance();
            }

            Pair<String, String> hostInfo = getHostInfo();

            DataCenterInfo dcInfo = buildDataCenterInfo(config);

            LeaseInfo.Builder leaseInfoBuilder = LeaseInfo.Builder.newBuilder().
                setRenewalIntervalInSecs(config.getInt(PROP_LEASE_RENEW_INTERVAL, LeaseInfo.DEFAULT_LEASE_RENEWAL_INTERVAL)).
                setDurationInSecs(config.getInt(PROP_LEASE_DURATION, LeaseInfo.DEFAULT_LEASE_DURATION));

            String appName = config.getString(PROP_NAME, UNKNOWN_APPLICATION).trim();

            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();

            int port = config.getInt(PROP_PORT, InstanceInfo.DEFAULT_PORT);
            int securePort = config.getInt(PROP_SECURE_PORT, InstanceInfo.DEFAULT_SECURE_PORT);
            boolean isStandardPortEnabled = config.getBoolean(PROP_PORT_ENABLED, true);
            boolean isSecurePortEnabled = config.getBoolean(PROP_SECURE_PORT_ENABLED, false);
            String hostName = dcInfo.getName() == DataCenterInfo.Name.Amazon ?
                    ((AmazonInfo)dcInfo).get(MetaDataKey.publicHostname) : hostInfo.second();

            builder.setAppName(appName).
                setDataCenterInfo(dcInfo).
                setIPAddr(hostInfo.first()).
                setHostName(hostName).
                setVersion(config.getString(PROP_VERSION, UNKNOWN_APPLICATION)).
                setSourceVersion(config.getString(PROP_SOURCE_VERSION, UNKNOWN_APPLICATION)).
                setPort(port).enablePort(PortType.UNSECURE, isStandardPortEnabled).
                setSecurePort(securePort).enablePort(PortType.SECURE, isSecurePortEnabled).
                setSID(config.getString(PROP_SID, UNKNOWN_APPLICATION)).
                setCountryId(config.getInt(PROP_COUNTRY_ID)).
                setStatusPageUrl(config.getString(PROP_STATUSPAGE_URLPATH,DEFAULT_STATUSPAGE_URLPATH), config.getString(PROP_STATUSPAGE_URL)).
                setHomePageUrl(config.getString(PROP_HOMEPAGE_URLPATH, DEFAULT_HOMEPAGE_URLPATH), config.getString(PROP_HOMEPAGE_URL)).
                setHealthCheckUrls(config.getString(PROP_HEALTHCHECK_URLPATH, DEFAULT_HEALTHCHECK_URLPATH), config.getString(PROP_HEALTHCHECK_URL),
                        config.getString(PROP_SECURE_HEALTHCHECK_URL)).
                setAcceptedVersions(config.getString(PROP_ACCEPTED_VERSIONS)).
                // Default VIP address to hostname for isolation, only do it
                // if the unsecure port is enabled
                setVIPAddress(isStandardPortEnabled ? config.getString(PROP_VIP_ADDRESS, hostName + ":" + port) : null).
                // Default Secure VIP address to hostname for isolation,
                // only do it if the secure port is enabled
                setSecureVIPAddress(isSecurePortEnabled ?  config.getString(PROP_SECURE_VIP_ADDRESS, hostName + ":" + securePort) : null).
                setASGName(config.getString(PROP_ASG_NAME)).
                build();

            if(!config.getBoolean(PROP_SERVETRAFFIC_ATSTARTUP, true)){
                builder.setStatus(InstanceStatus.STARTING);
            }

            for(Iterator<String> iter = config.subset(METADATA_NAMESPACE).getKeys();
                iter.hasNext();) {

                String key = iter.next();
                String value = config.getString(METADATA_NAMESPACE + key);
                builder.add(key, value);
            }

            if(_callback != null){
                _callback.onInit(builder);
            }

            _info = builder.build();
            _info.setLeaseInfo(leaseInfoBuilder.build());

            MonitorRegistry.getInstance().
                registerObject(Namespace.PLATFORM, _info);

        }catch (Throwable e){
            throw new IllegalArgumentException("Failed to init component: " +
                    "Make sure app is registered with the resource registry (http://wiki.netflix.com/clearspace/docs/DOC-16287)",
                    e);
        }
        _status = Status.INITIALIZED;
    }

    public InstanceInfo getInfo() {
        if(_status != Status.INITIALIZED) {
            throw new IllegalStateException("initComponent() is required first!");
        }
        return _info;
    }

    /**
     * Method for registering runtime application metadata.  Existing entries
     * will be overridden when keys match.  This data will be send over to the
     * discovery service on the next heartbeat.
     */
    public void registerAppMetadata(Map<String, String> appMetadata){
        if(_status != Status.INITIALIZED) {
            throw new IllegalStateException("initComponent() is required first!");
        }
        _info.registerRuntimeMetadata(appMetadata);
    }

    /**
     * Method for setting instance status.  New status will be sent over to the
     * discovery service on the next heartbeat.
     */
    public void setInstanceStatus(InstanceStatus status){
        if(_status != Status.INITIALIZED) {
            throw new IllegalStateException("initComponent() is required first!");
        }
        _info.setStatus(status);
    }

    /**
     * Validates if the application name is a valid name in the Resource Registry. This API relies
     * on the platform service to be available to do the validation. If it is not available, then it
     * deems the application as a valid one.
     * @param appName - The name of the application which needs to be validated.
     * @return - true if the application can be validated or if the platform service does not exist, false otherwise
     */
    //TODO: why is this method here?
    public boolean isValidApplicationName(String appName) {
        try {
            // Do the validation only for cloud
            if (UNKNOWN_APPLICATION.equals(appName)) {
                return false;
            }
            if (isCloud()) {
                NiwsClientConfig niwsConfig = new NiwsClientConfig();
                niwsConfig.setProperty(NiwsClientConfigKey.ConnectTimeout,
                        Integer.valueOf(5000));
                niwsConfig.setProperty(NiwsClientConfigKey.ReadTimeout,
                        Integer.valueOf(8000));

                RestClient client = RestClientFactory.createNewRestClient(niwsConfig);

                Application application =
                    DiscoveryManager.getInstance().getLookupService().getApplication("PLATFORMSERVICE");

                // Get the list of instances for platform service
                List<InstanceInfo> instances = application.getInstances();
                String httpUrl = null;
                List<InstanceInfo> validInstances = new ArrayList<InstanceInfo>();
                for (InstanceInfo instanceInfo : instances) {
                    // If there is anything that is running the DC, just
                    // ignore it.
                    if (DataCenterInfo.Name.Netflix.equals(instanceInfo
                            .getDataCenterInfo().getName())) {
                        continue;
                    }
                    validInstances.add(instanceInfo);
                }

                if (validInstances == null|| validInstances.size() <= 0) {
                    // Found no instances to make a call
                    s_logger.warn("Could not get instances for PLATFORMSERVICE to validate this application! Proceeding without validation.");
                    return true;
                }

                Random r = new Random();
                // Get an instance of platform service randomly
                InstanceInfo instanceInfo = validInstances.get(r
                        .nextInt(validInstances.size()));
                if (instanceInfo == null) {
                    s_logger.warn("The Instance Info for PLATFORMSERVICE does not exist to validate this application! Proceeding without validation.");
                    return true;
                }
                httpUrl = "http://" + instanceInfo.getHostName() + ":" + instanceInfo.getPort() + "/platformservice/REST/appReg/" + appName;

                IClientResponse response = null;
                NFMultivaluedMap<String, String> headers;

                headers = new NFMultivaluedMap<String, String>();
                headers.add(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_APPLICATION_XML);
                response = client.get(new URI(httpUrl), headers);
                if ((response.getStatus() == Response.Status.NO_CONTENT.getStatusCode())) {
                    return false;
                }else {
                    return true;
                }
            }
        } catch (Throwable t) {
            s_logger.warn("Cannot validate application with Platform Service for some reason. Check the server log for details",
                    t);
        }
        return true;
    }

    private boolean isCloud() {
        return DatacenterEnum.cloud.equals(NetflixConfiguration
                .getDatacenterEnum());
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdownComponent() throws ComponentException{
        _status = Status.SHUTDOWN;
        if(_info != null) {
            MonitorRegistry.getInstance().unRegisterObject(_info);
        }
    }

	@Override
    public void setProperties(Properties props) throws ComponentException {
		//no props yet?
	}

    private Pair<String, String> getHostInfo() {
        Pair<String,String> pair = new Pair<String, String>("","");
        try {
            pair.setFirst(InetAddress.getLocalHost().getHostAddress());
            pair.setSecond(InetAddress.getLocalHost().getHostName());

        } catch (UnknownHostException e) {
            s_logger.error(e);
        }
        return pair;
    }

    private DataCenterInfo buildDataCenterInfo(NetflixConfiguration config) {
        DataCenterInfo info = null;
        //Now rely on the netflix.datacenter value to determine whether to go and get the amazon info.
        // If the Amazon magic url does not give a response, just fail to avoid confusion as the instances
        // register without the public host name silently
        if (!DatacenterEnum.cloud.equals(config.getDatacenterEnum())) {
            info = InstanceInfo.DefaultDataCenterInfo.INSTANCE;
            s_logger.info("Datacenter is: " + Name.Netflix);
            return info;
        }
        try {
            info = AmazonInfo.Builder.newBuilder().autoBuild();
            s_logger.info("Datacenter is: " + Name.Amazon);
        }
        catch (Throwable e) {
            s_logger.error("Cannot initialize amazon info :" , e);
            throw new RuntimeException(e);
        }
        // Instance id being null means we could not get the amazon metadata
        AmazonInfo amazonInfo = (AmazonInfo)info;
        if (amazonInfo.get(MetaDataKey.instanceId) == null) {
            if (PROP_VALIDATE_INSTANCE_ID.get()) {
                throw new RuntimeException("Your datacenter is defined as cloud but we are not able to get the amazon metadata to register. \n" +
                		"Please set the property netflix.appinfo.validateInstanceId to false to ignore the metadata call");
            }
            // The property to not validate instance ids may be set for development and in that scenario, populate instance id
            // and public hostname with the hostname of the machine
            else {
                Map<String, String> metadataMap = new HashMap<String, String>();
                metadataMap.put(MetaDataKey.instanceId.getName(), getHostInfo().second());
                metadataMap.put(MetaDataKey.publicHostname.getName(),  getHostInfo().second());
                amazonInfo.setMetadata(metadataMap);
            }
        }
        // This might be a case of VPC where the instance id is not null, but public hostname might be null
        else if ((amazonInfo.get(MetaDataKey.publicHostname) == null) && (amazonInfo.get(MetaDataKey.localIpv4) != null)) {
            amazonInfo.getMetadata().put(MetaDataKey.publicHostname.getName(),  (amazonInfo.get(MetaDataKey.localIpv4)));
        }
        
        if(info.getName() == Name.Amazon){
            try{
                String hostNameForStubs =
                    amazonInfo.get(MetaDataKey.publicHostname);
                if (hostNameForStubs == null) {
                    return info;
                }
                //Needed since NAT device is used on Amazon cloud
                System.setProperty("java.rmi.server.hostname", hostNameForStubs);

                if(System.getProperty("com.sun.management.jmxremote.port") == null &&
                        FAST_PROP_JMX_ENABLED.get()) {
                    int port = FAST_PROP_JMX_PORT.get();
                    s_logger.info("Creating RMI registry on: "+port);
                    LocateRegistry.createRegistry(port);

                    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

                    Map<String,Object> env = new HashMap<String,Object>();

                    s_logger.info("Creating RMI Connector server on: ");

                    JMXServiceURL url =
                        new JMXServiceURL("service:jmx:rmi://" +
                                hostNameForStubs +":" + port +
                                "/jndi/rmi://"+ hostNameForStubs + ":" +port +
                                "/jmxrmi");

                    JMXConnectorServer jmxConn =
                        JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);
                    s_logger.info("Starting JMX Connector Server on: " +
                            url.getURLPath());
                    jmxConn.start();
                }
            }catch(Exception e){
                s_logger.error(e);
            }
        }
        return info;
    }
}