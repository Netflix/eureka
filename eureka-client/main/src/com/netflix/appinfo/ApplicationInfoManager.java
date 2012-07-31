/*
 * ApplicationInfoManager.java
 *
 * $Header: //depot/commonlibraries/platform/main/ipc/src/com/netflix/appinfo/ApplicationInfoManager.java#2 $
 * $DateTime: 2012/06/13 17:18:10 $
 *
 * Copyright (c) 2008 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.shared.Pair;

/**
 * Standard way to access basic application information. This information will
 * be used by our monitoring and/or discovery services.
 * 
 * {@link InstanceInfo} is populated once during platform initialization so it
 * is pretty much static data after the platform is fully initialized.
 * 
 * Application info can be specified in two ways:
 * <ul>
 * <li>Through the existing application override configuration. Typically this
 * should be set by the our deployment tool (e.g. WebCM)
 * <p>
 * Example:
 * 
 * <pre>
 * {@code
 *      netflix.appinfo.name=www # Please read http://wiki.netflix.com/clearspace/docs/DOC-16287
 *      netflix.appinfo.sid=123
 *      netflix.appinfo.version=v1
 *      netflix.appinfo.port=7001
 *      netflix.appinfo.dependencies=ST_DB,WCS,WNS,ESB,WSS
 *      netflix.appinfo.metadata.name1=value2
 *      netflix.appinfo.metadata.name2=value3
 *      ...
 * }
 * </pre>
 * 
 * <li>Or via a callback mechanism by registering your own class implementing
 * 
 * <pre>
 * {@code Callback<ApplicationInfo>}
 * </pre>
 * 
 * you'll need to set the fully qualified class in the app override
 * configuration ( {@code netflix.appinfo.callback=com.netflix.appX.Callback}
 * </ul>
 * 
 * @author gkim
 */
public class ApplicationInfoManager {

    private static final DynamicPropertyFactory INSTANCE = com.netflix.config.DynamicPropertyFactory
    .getInstance();
    private static final Configuration config = (Configuration) INSTANCE
    .getBackingConfigurationSource();
    private static final String PROP_ASG_NAME = "NETFLIX_AUTO_SCALE_GROUP";
    public static final String UNKNOWN_APPLICATION = "unknown";
    static final String DEFAULT_HEALTHCHECK_URLPATH = "/healthcheck";
    public static final String NAME = "APPINFO";
    public static final String CONFIG_NAME = "platform:appinfo";

    public static final String PROP_DATACENTER = "netflix.datacenter";
    public static final String NAMESPACE = "netflix.appinfo.";
    public static final String METADATA_NAMESPACE = "netflix.appinfo.metadata.";
    public static final String PROP_NAME = NAMESPACE + "name";
    public static final String PROP_DEPS = NAMESPACE + "dependencies";
    public static final String PROP_SERVETRAFFIC_ATSTARTUP = NAMESPACE
    + "traffic.enabled";
    public static final String PROP_PORT = NAMESPACE + "port";
    public static final String PROP_PORT_ENABLED = PROP_PORT + ".enabled";
    public static final String PROP_SECURE_PORT = NAMESPACE + "securePort";
    public static final String PROP_SECURE_PORT_ENABLED = PROP_SECURE_PORT
    + ".enabled";
    public static final String PROP_COUNTRY_ID = NAMESPACE + "countryId";
    public static final String PROP_VERSION = NAMESPACE + "version";
    public static final String PROP_SOURCE_VERSION = NAMESPACE
    + "sourceVersion";
    public static final String PROP_BUILD_DATE = NAMESPACE + "buildDate";
    public static final String PROP_CALLBACK = NAMESPACE + "callback";
    public static final String PROP_SID = NAMESPACE + "sid";
    public static final String PROP_LEASE_RENEW_INTERVAL = NAMESPACE
    + "lease.renewalInterval";
    public static final String PROP_LEASE_DURATION = NAMESPACE
    + "lease.duration";

    static final String PROP_HOMEPAGE_URLPATH = NAMESPACE + "homePageUrlPath";
    static final String PROP_HOMEPAGE_URL = NAMESPACE + "homePageUrl";
    static final String PROP_STATUSPAGE_URLPATH = NAMESPACE
    + "statusPageUrlPath";
    static final String PROP_STATUSPAGE_URL = NAMESPACE + "statusPageUrl";
    static final String PROP_HEALTHCHECK_URLPATH = NAMESPACE
    + "healthCheckUrlPath";
    static final String PROP_HEALTHCHECK_URL = NAMESPACE + "healthCheckUrl";
    static final String PROP_SECURE_HEALTHCHECK_URL = NAMESPACE
    + "secureHealthCheckUrl";
    static final String PROP_SECURE_VIP_ADDRESS = NAMESPACE
    + "secureVipAddress";
    static final String PROP_VIP_ADDRESS = NAMESPACE + "vipAddress";
    static final String DEFAULT_STATUSPAGE_URLPATH = "/Status";
    static final String DEFAULT_HOMEPAGE_URLPATH = "/";

    private static final String PROP_ACCEPTED_VERSIONS = NAMESPACE
    + "acceptedVersions";

    // TODO: probably should move this out to another component
    // These props are currently only applicable in the cloud
    private static final DynamicIntProperty FAST_PROP_JMX_PORT = INSTANCE
    .getIntProperty("netflix.jmx.port", 7500);
    private static final DynamicBooleanProperty FAST_PROP_JMX_ENABLED = INSTANCE
    .getBooleanProperty("netflix.jmx.enabled", true);
    private static final DynamicBooleanProperty PROP_VALIDATE_INSTANCE_ID = INSTANCE
    .getBooleanProperty("netflix.appinfo.validateInstanceId", true);

    private static final ApplicationInfoManager s_instance = new ApplicationInfoManager();
    private InstanceInfo _info;

    private static final Logger s_logger = LoggerFactory
    .getLogger(ApplicationInfoManager.class);

    private ApplicationInfoManager() {
    }

    public static ApplicationInfoManager getInstance() {
        return s_instance;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public void initComponent() {
        try {

            Pair<String, String> hostInfo = getHostInfo();

            DataCenterInfo dcInfo = buildDataCenterInfo();

            LeaseInfo.Builder leaseInfoBuilder = LeaseInfo.Builder
            .newBuilder()
            .setRenewalIntervalInSecs(
                    INSTANCE.getIntProperty(PROP_LEASE_RENEW_INTERVAL,
                            LeaseInfo.DEFAULT_LEASE_RENEWAL_INTERVAL)
                            .get())
                            .setDurationInSecs(
                                    INSTANCE.getIntProperty(PROP_LEASE_DURATION,
                                            LeaseInfo.DEFAULT_LEASE_DURATION).get());

            String appName = INSTANCE
            .getStringProperty(PROP_NAME, UNKNOWN_APPLICATION).get()
            .trim();

            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();

            int port = INSTANCE.getIntProperty(PROP_PORT,
                    InstanceInfo.DEFAULT_PORT).get();
            int securePort = INSTANCE.getIntProperty(PROP_SECURE_PORT,
                    InstanceInfo.DEFAULT_SECURE_PORT).get();
            boolean isStandardPortEnabled = INSTANCE.getBooleanProperty(
                    PROP_PORT_ENABLED, true).get();
            boolean isSecurePortEnabled = INSTANCE.getBooleanProperty(
                    PROP_SECURE_PORT_ENABLED, false).get();
            String hostName = dcInfo.getName() == DataCenterInfo.Name.Amazon ? ((AmazonInfo) dcInfo)
                    .get(MetaDataKey.publicHostname) : hostInfo.second();

                    builder.setAppName(appName)
                    .setDataCenterInfo(dcInfo)
                    .setIPAddr(hostInfo.first())
                    .setHostName(hostName)
                    .setVersion(
                            INSTANCE.getStringProperty(PROP_VERSION,
                                    UNKNOWN_APPLICATION).get())
                                    .setSourceVersion(
                                            INSTANCE.getStringProperty(PROP_SOURCE_VERSION,
                                                    UNKNOWN_APPLICATION).get())
                                                    .setPort(port)
                                                    .enablePort(PortType.UNSECURE, isStandardPortEnabled)
                                                    .setSecurePort(securePort)
                                                    .enablePort(PortType.SECURE, isSecurePortEnabled)
                                                    .setSID(INSTANCE.getStringProperty(PROP_SID,
                                                            UNKNOWN_APPLICATION).get())
                                                            .setCountryId(
                                                                    INSTANCE.getIntProperty(PROP_COUNTRY_ID, 0).get())
                                                                    .setStatusPageUrl(
                                                                            INSTANCE.getStringProperty(PROP_STATUSPAGE_URLPATH,
                                                                                    DEFAULT_STATUSPAGE_URLPATH).get(),
                                                                                    INSTANCE.getStringProperty(PROP_STATUSPAGE_URL,
                                                                                            null).get())
                                                                                            .setHomePageUrl(
                                                                                                    INSTANCE.getStringProperty(PROP_HOMEPAGE_URLPATH,
                                                                                                            DEFAULT_HOMEPAGE_URLPATH).get(),
                                                                                                            INSTANCE.getStringProperty(PROP_HOMEPAGE_URL, null)
                                                                                                            .get())
                                                                                                            .setHealthCheckUrls(
                                                                                                                    INSTANCE.getStringProperty(
                                                                                                                            PROP_HEALTHCHECK_URLPATH,
                                                                                                                            DEFAULT_HEALTHCHECK_URLPATH).get(),
                                                                                                                            INSTANCE.getStringProperty(PROP_HEALTHCHECK_URL,
                                                                                                                                    null).get(),
                                                                                                                                    INSTANCE.getStringProperty(
                                                                                                                                            PROP_SECURE_HEALTHCHECK_URL, null).get())
                                                                                                                                            .setAcceptedVersions(
                                                                                                                                                    INSTANCE.getStringProperty(PROP_ACCEPTED_VERSIONS,
                                                                                                                                                            null).get())
                                                                                                                                                            .
                                                                                                                                                            // Default VIP address to hostname for isolation, only do it
                                                                                                                                                            // if the unsecure port is enabled
                                                                                                                                                            setVIPAddress(
                                                                                                                                                                    isStandardPortEnabled ? INSTANCE.getStringProperty(
                                                                                                                                                                            PROP_VIP_ADDRESS, hostName + ":" + port)
                                                                                                                                                                            .get() : null)
                                                                                                                                                                            .
                                                                                                                                                                            // Default Secure VIP address to hostname for isolation,
                                                                                                                                                                            // only do it if the secure port is enabled
                                                                                                                                                                            setSecureVIPAddress(
                                                                                                                                                                                    isSecurePortEnabled ? INSTANCE.getStringProperty(
                                                                                                                                                                                            PROP_SECURE_VIP_ADDRESS,
                                                                                                                                                                                            hostName + ":" + securePort).get() : null)
                                                                                                                                                                                            .setASGName(
                                                                                                                                                                                                    INSTANCE.getStringProperty(PROP_ASG_NAME, null)
                                                                                                                                                                                                    .get()).build();

                    if (!INSTANCE.getBooleanProperty(PROP_SERVETRAFFIC_ATSTARTUP, true)
                            .get()) {
                        builder.setStatus(InstanceStatus.STARTING);
                    }

                    for (Iterator<String> iter = config.subset(METADATA_NAMESPACE)
                            .getKeys(); iter.hasNext();) {
                        String key = iter.next();
                        String value = config.getString(METADATA_NAMESPACE + key);
                        builder.add(key, value);
                    }

                    _info = builder.build();
                    _info.setLeaseInfo(leaseInfoBuilder.build());

        } catch (Throwable e) {
            throw new IllegalArgumentException(
                    "Failed to init component: "
                    + "Make sure app is registered with the resource registry (http://wiki.netflix.com/clearspace/docs/DOC-16287)",
                    e);
        }
    }

    public InstanceInfo getInfo() {
        return _info;
    }

    /**
     * Method for registering runtime application metadata. Existing entries
     * will be overridden when keys match. This data will be send over to the
     * discovery service on the next heartbeat.
     */
    public void registerAppMetadata(Map<String, String> appMetadata) {
        _info.registerRuntimeMetadata(appMetadata);
    }

    /**
     * Method for setting instance status. New status will be sent over to the
     * discovery service on the next heartbeat.
     */
    public void setInstanceStatus(InstanceStatus status) {
        _info.setStatus(status);
    }

    private Pair<String, String> getHostInfo() {
        Pair<String, String> pair = new Pair<String, String>("", "");
        try {
            pair.setFirst(InetAddress.getLocalHost().getHostAddress());
            pair.setSecond(InetAddress.getLocalHost().getHostName());

        } catch (UnknownHostException e) {
            s_logger.error("Cannot get host info", e);
        }
        return pair;
    }

    private DataCenterInfo buildDataCenterInfo() {
        DataCenterInfo info = null;
        // Now rely on the netflix.datacenter value to determine whether to go
        // and get the amazon info.
        // If the Amazon magic url does not give a response, just fail to avoid
        // confusion as the instances
        // register without the public host name silently
        if (!"cloud".equals(ConfigurationManager.getDeploymentContext()
                .getDeploymentDatacenter())) {
            info = InstanceInfo.DefaultDataCenterInfo.INSTANCE;
            s_logger.info("Datacenter is: " + Name.Netflix);
            return info;
        }
        try {
            info = AmazonInfo.Builder.newBuilder().autoBuild();
            s_logger.info("Datacenter is: " + Name.Amazon);
        } catch (Throwable e) {
            s_logger.error("Cannot initialize amazon info :", e);
            throw new RuntimeException(e);
        }
        // Instance id being null means we could not get the amazon metadata
        AmazonInfo amazonInfo = (AmazonInfo) info;
        if (amazonInfo.get(MetaDataKey.instanceId) == null) {
            if (PROP_VALIDATE_INSTANCE_ID.get()) {
                throw new RuntimeException(
                        "Your datacenter is defined as cloud but we are not able to get the amazon metadata to register. \n"
                        + "Please set the property netflix.appinfo.validateInstanceId to false to ignore the metadata call");
            }
            // The property to not validate instance ids may be set for
            // development and in that scenario, populate instance id
            // and public hostname with the hostname of the machine
            else {
                Map<String, String> metadataMap = new HashMap<String, String>();
                metadataMap.put(MetaDataKey.instanceId.getName(), getHostInfo()
                        .second());
                metadataMap.put(MetaDataKey.publicHostname.getName(),
                        getHostInfo().second());
                amazonInfo.setMetadata(metadataMap);
            }
        }
        // This might be a case of VPC where the instance id is not null, but
        // public hostname might be null
        else if ((amazonInfo.get(MetaDataKey.publicHostname) == null)
                && (amazonInfo.get(MetaDataKey.localIpv4) != null)) {
            amazonInfo.getMetadata().put(MetaDataKey.publicHostname.getName(),
                    (amazonInfo.get(MetaDataKey.localIpv4)));
        }

        if (info.getName() == Name.Amazon) {
            try {
                String hostNameForStubs = amazonInfo
                .get(MetaDataKey.publicHostname);
                if (hostNameForStubs == null) {
                    return info;
                }
                // Needed since NAT device is used on Amazon cloud
                System.setProperty("java.rmi.server.hostname", hostNameForStubs);

                if (System.getProperty("com.sun.management.jmxremote.port") == null
                        && FAST_PROP_JMX_ENABLED.get()) {
                    int port = FAST_PROP_JMX_PORT.get();
                    s_logger.info("Creating RMI registry on: " + port);
                    LocateRegistry.createRegistry(port);

                    MBeanServer mbs = ManagementFactory
                    .getPlatformMBeanServer();

                    Map<String, Object> env = new HashMap<String, Object>();

                    s_logger.info("Creating RMI Connector server on: ");

                    JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://"
                            + hostNameForStubs + ":" + port + "/jndi/rmi://"
                            + hostNameForStubs + ":" + port + "/jmxrmi");

                    JMXConnectorServer jmxConn = JMXConnectorServerFactory
                    .newJMXConnectorServer(url, env, mbs);
                    s_logger.info("Starting JMX Connector Server on: "
                            + url.getURLPath());
                    jmxConn.start();
                }
            } catch (Exception e) {
                s_logger.error("Error initializing Application Info", e);
            }
        }
        return info;
    }
}