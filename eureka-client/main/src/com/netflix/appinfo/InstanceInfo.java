/*
 * InstanceInfo.java
 *
 * $Header: //depot/commonlibraries/platform/main/ipc/src/com/netflix/appinfo/InstanceInfo.java#7 $
 * $DateTime: 2012/06/13 17:18:10 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.converters.Auto;
import com.netflix.niws.IPayload;
import com.netflix.niws.PayloadConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.netflix.appinfo.ApplicationInfoManager.*;

/**
 * Information about the running application instance
 *
 * @author gkim
 */
@PayloadConverter("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("instance")
public class InstanceInfo implements IPayload {

    private static final Pattern VIP_ATTRIBUTES_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");
    
    public enum InstanceStatus {

        UP, DOWN, STARTING, OUT_OF_SERVICE, UNKNOWN;

        public static InstanceStatus toEnum(String s){
            for(InstanceStatus e : InstanceStatus.values()){
                if(e.name().equalsIgnoreCase(s)){
                    return e;
                }
            }
            return UNKNOWN;
        }
    }
    

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((getId() == null) ? 0 : getId().hashCode());
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
        InstanceInfo other = (InstanceInfo) obj;
        if (getId() == null) {
            if (other.getId() != null)
                return false;
        } else if (!getId().equals(other.getId()))
            return false;
        return true;
    }

    public enum PortType {
        SECURE, UNSECURE
    }

    private static final Logger logger = LoggerFactory.getLogger(InstanceInfo.class); 

    public static final class Builder {

        private static final String HOSTNAME_INTERPOLATION_EXPRESSION = "${netflix.appinfo.hostname}";
        private static final String COLON = ":";
        private static final String HTTPS_PROTOCOL = "https://";
        private static final String HTTP_PROTOCOL = "http://";

        @XStreamOmitField
        private InstanceInfo result;

        private Builder() {
            result = new InstanceInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        
        public Builder setAppName(String group) {
            result.app = group;
            if (result.app != null) {
                result.app = result.app.toUpperCase();
            }
            return this;
        }

        /**
         * Sets the fully qualified hostname of this running instance
         */
        public Builder setHostName(String host) {
            result.hostName = host;
            return this;
        }

        /**
         * Sets the initial status of instance in {@link InstanceInfo}
         */
        public Builder setStatus(InstanceStatus status) {
            result.status = status;
            return this;
        }
        
        /**
         * Sets the initial status of instance in {@link InstanceInfo}
         */
        public Builder setOverriddenStatus(InstanceStatus status) {
            result.overriddenstatus = status;
            return this;
        }

        /**
         * Sets the ip address of this running instance
         */
        public Builder setIPAddr(String ip) {
            result.ipAddr = ip;
            return this;
        }

        /**
         * Sets the specification version of this application
         */
        public Builder setVersion(String ver) {
            result.version = ver;
            return this;
        }

        /**
         * Sets the source version (e.g. perforce changelist#)
         */
        public Builder setSourceVersion(String sourceVer) {
            result.sourceVersion = sourceVer;
            return this;
        }

        /**
         * Sets the source version build date
         */
        public Builder setBuildDate(String dateStr) {
            result.buildDate = dateStr;
            return this;
        }

        /**
         * Sets the identity of this application instance (e.g. www101)
         */
        @Deprecated
        public Builder setSID(String sid) {
            result.sid = sid;
            return this;
        }

        /**
         * Sets the port number of service
         */
        public Builder setPort(int port) {
            result.port = port;
            return this;
        }

        /**
         * Sets the secured port number of service
         */
        public Builder setSecurePort(int port) {
            result.securePort = port;
            return this;
        }

        public Builder enablePort(PortType type, boolean isEnabled) {
            if (type == PortType.SECURE) {
                result.isSecurePortEnabled = isEnabled;
            } else {
                result.isUnsecurePortEnabled = isEnabled;
            }
            return this;
        }

        @Deprecated
        public Builder setCountryId(int id) {
            result.countryId = id;
            return this;
        }

        /**
         * Sets the absolute home page URL for this instance. The users can provide the homePageUrlPath if
         * the home page resides in the same instance talking to discovery, else in the cases where the instance is
         * a proxy for some other server, it can provide the full url. If the full url is provided it takes precedence.
         *
         * The full url should follow the format http://${netflix.appinfo.hostname}:7001/ where the value ${netflix.appinfo.hostname}
         * is replaced at runtime.
         *
         * @param homePageUrlPath - The URL path for home page for this instance
         * @param explicitUrl - The full URL for the home page
         * @return - Builder instance
         */
        public Builder setHomePageUrl(String relativeUrl, String explicitUrl) {
            if (explicitUrl != null) {
                result.homePageUrl = explicitUrl.replace(HOSTNAME_INTERPOLATION_EXPRESSION, result.hostName);
            }else if(relativeUrl != null){
                result.homePageUrl = HTTP_PROTOCOL + result.hostName + COLON + result.port + relativeUrl;
            }
            return this;
        }

        /**
         * Sets the absolute status page URL for this instance. The users can provide the statusPageUrlPath if
         * the status page resides in the same instance talking to discovery, else in the cases where the instance is
         * a proxy for some other server, it can provide the full url. If the full url is provided it takes precedence.
         *
         * The full url should follow the format http://${netflix.appinfo.hostname}:7001/Status where the value ${netflix.appinfo.hostname}
         * is replaced at runtime.
         *
         * @param relativeUrl - The URL path for status page for this instance
         * @param explicitUrl - The full URL for the status page
         * @return - Builder instance
         */
        public Builder setStatusPageUrl(String relativeUrl, String explicitUrl) {
            if (explicitUrl != null) {
                result.statusPageUrl = explicitUrl.replace(HOSTNAME_INTERPOLATION_EXPRESSION, result.hostName);
            }else if(relativeUrl != null){
                result.statusPageUrl =  HTTP_PROTOCOL + result.hostName + COLON + result.port + relativeUrl;
            }
            return this;
        }

        /**
         * Sets the absolute health check URL for this instance for both secure and non-secure communication
         * The users can provide the healthCheckUrlPath if the healthcheck page resides in the same instance talking to discovery,
         * else in the cases where the instance is a proxy for some other server, it can provide the full url.
         *  If the full url is provided it takes precedence.
         *
         * The full url should follow the format http://${netflix.appinfo.hostname}:7001/healthcheck where the value ${netflix.appinfo.hostname}
         * is replaced at runtime.
         *
         * @param relativeUrl - The URL path for healthcheck page for this instance
         * @param explicitUrl - The full URL for the healthcheck page
         * @param securehealthCheckUrl - The full URL for the secure healthcheck page
         * @return - Builder instance
         */
        public Builder setHealthCheckUrls(String relativeUrl,
                String explicitUrl, String secureExplicitUrl) {
            if(explicitUrl != null){
                result.healthCheckUrl = explicitUrl.replace(HOSTNAME_INTERPOLATION_EXPRESSION, result.hostName);
            }else if (result.isUnsecurePortEnabled) {
                result.healthCheckUrl = HTTP_PROTOCOL + result.hostName + COLON + result.port + relativeUrl;
            }

            if(secureExplicitUrl != null){
                result.secureHealthCheckUrl = secureExplicitUrl.replace(HOSTNAME_INTERPOLATION_EXPRESSION,result.hostName);
            }else if(result.isSecurePortEnabled) {
                result.secureHealthCheckUrl = HTTPS_PROTOCOL + result.hostName + COLON + result.securePort + relativeUrl;
            }
            return this;
        }

        /**
         * Sets the Virtual Internet Protocol address for this instance. The address should follow the
         * format <hostname:port> This address needs to be resolved into a real address for
         * communicating with this instance.
         * @param vipAddress - The Virtual Internet Protocol address of this instance
         * @return - Builder instance
         */
        public Builder setVIPAddress(String vipAddress) {
            result.vipAddress = resolveDeploymentContextBasedVipAddresses(vipAddress);
            return this;
        }

        /**
         * Sets the Secure Virtual Internet Protocol address for this instance. The address should follow the
         * format <hostname:port> This address needs to be resolved into a real address for
         * communicating with this instance.
         * @param secureVipAddress - The Virtual Internet Protocol address of this instance
         * @return - Builder instance
         */
        public Builder setSecureVIPAddress(String secureVIPAddress) {
            result.secureVipAddress = resolveDeploymentContextBasedVipAddresses(secureVIPAddress);
            return this;
        }

        /**
         * Sets the regular expression which determines what versions this instance will accept
         * @param acceptedVersions - A regular expression that determines the version match
         */
        public Builder setAcceptedVersions(String acceptedVersions) {
            result.acceptedVersions = acceptedVersions;
            return this;
        }

        /**
         * Sets the datacenter
         */
        public Builder setDataCenterInfo(DataCenterInfo datacenter) {
            result.dataCenterInfo = datacenter;
            return this;
        }

        public void setLeaseInfo(LeaseInfo info) {
            result.leaseInfo = info;
        }

        /**
         * Add arbitrary metadata to running instance
         */
        public Builder add(String key, String val) {
            result.metadata.put(key, val);
            return this;
        }

        public Builder setMetadata(Map<String, String> mt) {
            result.metadata = mt;
            return this;
        }

        /**
         * Returns the encapsulated instance info even it it is not built fully
         * @return - InstanceInfo
         */
        public InstanceInfo getRawInstance() {
            return result;
        }

        /**
         * Build the {@link InstanceInfo}
         */
        public InstanceInfo build() {
            if (!isInitialized()) {
                throw new IllegalStateException("name is required!");
            }
            return result;
        }

        public boolean isInitialized() {
            //TODO: verify that all required fields are populated
            return (result.app != null);
        }

        public Builder setASGName(String asgName) {
            result.asgName = asgName;
            return this;
        }
    }
    public final static int DEFAULT_PORT = 7001;
    public final static int DEFAULT_SECURE_PORT = 7002;
    public final static int DEFAULT_COUNTRY_ID = 1; //US
    private volatile String app;
    private volatile String ipAddr;
    private volatile String version = "unknown";
    private volatile String sourceVersion = "unknown";
    private volatile String buildDate;
    private volatile String sid = "na";

    private volatile int port = DEFAULT_PORT;
    private volatile int securePort = DEFAULT_SECURE_PORT;
    @Auto
    private volatile String homePageUrl;
    @Auto
    private volatile String statusPageUrl;
    @Auto
    private volatile String healthCheckUrl;
    @Auto
    private volatile String secureHealthCheckUrl;
    @Auto
    private volatile String vipAddress;
    @Auto
    private volatile String secureVipAddress;
    private volatile boolean isSecurePortEnabled = false;
    private volatile boolean isUnsecurePortEnabled = true;
    private volatile int countryId = DEFAULT_COUNTRY_ID; //Defaults to US

    //Fields that can potentially change
    private volatile DataCenterInfo dataCenterInfo;
    private volatile String hostName;
    private volatile InstanceStatus status = InstanceStatus.UP;
    private volatile InstanceStatus overriddenstatus = InstanceStatus.UNKNOWN;
    @XStreamOmitField
    private volatile boolean isInstanceInfoDirty = false;
    private volatile String identifyingAttribute = "EC2_INSTANCE_ID";
    private volatile LeaseInfo leaseInfo;
    @Auto
    private volatile String acceptedVersions;
    @Auto
    private volatile Boolean isCoordinatingDiscoveryServer = Boolean.FALSE;
    @XStreamAlias("metadata")
    private volatile Map<String, String> metadata = new ConcurrentHashMap<String, String>();
    @Auto
    private volatile Long lastUpdatedTimestamp = System.currentTimeMillis();
    @Auto
    private volatile Long lastDirtyTimestamp = System.currentTimeMillis();
    @Auto
    private volatile ActionType actionType;
    
    @Auto
    private volatile String asgName;
  
    private InstanceInfo() {
    }

    /**
     * Returns the app name (e.g. WNS, QueueService, ...)
     */
    public String getAppName() {
        return app;
    }

    /**
     * Returns the fully qualified hostname of this running instance
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Returns the unique id within the containing application
     */
    public String getId() {
        if (dataCenterInfo.getName() == Name.Amazon){
            return ((AmazonInfo) dataCenterInfo).get(MetaDataKey.instanceId);
        } else {
            return hostName;
        }
    }

    /**
     * Returns the ip address of this running instance
     */
    public String getIPAddr() {
        return ipAddr;
    }

    /**
     * Returns the port number of service
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the specification version of this application
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the instance status
     */
    public InstanceStatus getStatus() {
        return status;
    }

    /**
     * Returns the instance overridden status
     */
    public InstanceStatus getOverriddenStatus() {
        return overriddenstatus;
    }

    /**
     * Returns datacenter specifics
     */
    public DataCenterInfo getDataCenterInfo() {
        return dataCenterInfo;
    }

    /**
     * Returns the lease info
     */
    public LeaseInfo getLeaseInfo() {
        return leaseInfo;
    }

    /**
     * Sets the lease info (done by discovery service)
     */
    public void setLeaseInfo(LeaseInfo info) {
        leaseInfo = info;
    }

    /**
     * Returns all other name,value pairs associated w/ this running instance
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Deprecated
    public int getCountryId() {
        return countryId;
    }

    public int getSecurePort() {
        return securePort;
    }

    public boolean isPortEnabled(PortType type) {
        if (type == PortType.SECURE) {
            return isSecurePortEnabled;
        } else {
            return isUnsecurePortEnabled;
        }
    }

    /**
     * Gets the absolute URL for the Home Page for this instance
     * @return - The  Home Page Url
     */
    public String getHomePageUrl() {
        return homePageUrl;
    }

    public long getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public void setLastUpdatedTimestamp() {
        this.lastUpdatedTimestamp = System.currentTimeMillis();
    }

    /**
     * Gets the absolute URL for the Status Page for this instance
     * @return - The Status Page Url
     */
    public String getStatusPageUrl() {
        return statusPageUrl;
    }

    /**
     * Gets the absolute URLs for the health check page for both secure
     * and non-secure protocols. If the port is not enabled then the URL is
     * excluded.
     * @return - A Set containing the string representation of health check urls
     * for secure and non secure protocols
     */
    public Set<String> getHealthCheckUrls() {
        Set<String> healthCheckUrlSet = new LinkedHashSet<String>();
        if (this.isUnsecurePortEnabled) {
            healthCheckUrlSet.add(healthCheckUrl);
        }
        if (this.isSecurePortEnabled) {
            healthCheckUrlSet.add(secureHealthCheckUrl);
        }
        return healthCheckUrlSet;
    }

    /**
     * Gets the Virtual Internet Protocol address for this instance. Defaults to
     * hostname if not specified.
     * @return - The Virtual Internet Protocol address
     */
    public String getVIPAddress() {
        return vipAddress;
    }

    /**
     * Get the Secure Virtual Internet Protocol address for this instance. Defaults to
     * hostname if not specified.
     * @return - The Secure Virtual Internet Protocol address
     */
    public String getSecureVipAddress() {
        return secureVipAddress;
    }

    /**
     * Gets the regular expression that determines what versions this instance can
     * accept. Returns null if it is not defined using the property netflix.appinfo.acceptedVersions.
     *
     * @return - A regular expression that matches version(s) that are supported
     */
    public String getAcceptedVersions() {
        return acceptedVersions;
    }

    public Long getLastDirtyTimestamp() {
        return lastDirtyTimestamp;
    }

    public void setLastDirtyTimestamp(Long lastDirtyTimestamp) {
        this.lastDirtyTimestamp = lastDirtyTimestamp;
    }

    /**
     * Called by {@link ApplicationInfoManager} only
     */
    public synchronized void setStatus(InstanceStatus status) {
        if(this.status != status){
            this.status = status;
            setIsDirty(true);
        }
    }
    
    public synchronized void setOverriddenStatus(InstanceStatus status) {
        if(this.overriddenstatus != status){
            this.overriddenstatus = status;
       }
    }
    
    

    /**
     * Called by {@link ApplicationInfoManager} only.  Will override values when
     * entry already exists
     */
    synchronized void registerRuntimeMetadata(Map<String, String> runtimeMetadata) {
        metadata.putAll(runtimeMetadata);
        setIsDirty(true);
    }

    /**
     * Returns whether any state changed so that {@link DiscoveryClient} can
     * check whether to retransmit info or not on the next heartbeat
     */
    public boolean isDirty() {
        return isInstanceInfoDirty;
    }

    public void setIsDirty(boolean b) {
        isInstanceInfoDirty = b;
        this.lastDirtyTimestamp = System.currentTimeMillis();
    }

    /**
     * Sets a flag if this instance is the same as the discovery server that is
     * return the instances. This flag is used by the discovery clients to identity
     * the discovery server which is co-ordinating/returning the information
     */
    public void setIsCoordinatingDiscoveryServer() {
        String instanceId = getId();
        if ((instanceId != null) && (instanceId.equals(ApplicationInfoManager.getInstance().getInfo().getId()))) {
            isCoordinatingDiscoveryServer = Boolean.TRUE;
        } else {
            isCoordinatingDiscoveryServer = Boolean.FALSE;
        }
    }

    /**
     * Finds if this instance is the co-ordinating discovery server
     * @return - true, if this instance is the co-ordinating discovery server, false otherwise
     */
    public Boolean isCoordinatingDiscoveryServer() {
        return isCoordinatingDiscoveryServer;
    }

    /**
     * Refresh instance info - currently only used when in amazon's datacenter
     * as a public ip can change whenever an EIP is associated or de-associated
     */
    public synchronized void refresh() {
        try {
            if (dataCenterInfo.getName() == Name.Amazon) {
                //Get new datacenter info to see if anything changed
                AmazonInfo newInfo = AmazonInfo.Builder.newBuilder().autoBuild();
                String newHostname = newInfo.get(MetaDataKey.publicHostname);
                String existingHostname = ((AmazonInfo) dataCenterInfo).get(MetaDataKey.publicHostname);
                if (newHostname != null && !newHostname.equals(existingHostname)) {
                    //public dns has changed on us, re-sync it
                    logger.warn("DiscoveryClient: Our public dns has changed on us from: "
                            + existingHostname + " => " + newHostname);
                    this.hostName = newHostname;
                    this.dataCenterInfo = newInfo;
                    InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();

                    builder.setHostName(newHostname).
                        setDataCenterInfo(newInfo).
                        setStatusPageUrl(DynamicPropertyFactory.getInstance().getStringProperty(PROP_STATUSPAGE_URLPATH,DEFAULT_STATUSPAGE_URLPATH).get(),
                                DynamicPropertyFactory.getInstance().getStringProperty(PROP_STATUSPAGE_URL, null).get()).
                        setHomePageUrl(DynamicPropertyFactory.getInstance().getStringProperty(PROP_HOMEPAGE_URLPATH, DEFAULT_HOMEPAGE_URLPATH).get(),
                                DynamicPropertyFactory.getInstance().getStringProperty(PROP_HOMEPAGE_URL, null).get()).
                        setHealthCheckUrls(DynamicPropertyFactory.getInstance().getStringProperty(PROP_HEALTHCHECK_URLPATH, DEFAULT_HEALTHCHECK_URLPATH).get(),
                                DynamicPropertyFactory.getInstance().getStringProperty(PROP_HEALTHCHECK_URL, null).get(), DynamicPropertyFactory.getInstance().getStringProperty(PROP_SECURE_HEALTHCHECK_URL, null).get()).
                        // if the unsecure port is enabled
                        setVIPAddress(this.isUnsecurePortEnabled ?  DynamicPropertyFactory.getInstance().getStringProperty(PROP_VIP_ADDRESS, hostName + ":" + this.port).get(): null).
                        // only do it if the secure port is enabled
                        setSecureVIPAddress(this.isSecurePortEnabled ? DynamicPropertyFactory.getInstance().getStringProperty(PROP_SECURE_VIP_ADDRESS, hostName + ":" + securePort).get() : null);

                    this.statusPageUrl = builder.result.statusPageUrl;
                    //TODO: remove homepage url
                    this.homePageUrl = builder.result.statusPageUrl;
                    this.healthCheckUrl = builder.result.healthCheckUrl;
                    this.secureHealthCheckUrl = builder.result.secureHealthCheckUrl;
                    this.vipAddress = builder.result.vipAddress;
                    this.secureVipAddress = builder.result.secureVipAddress;
                    setIsDirty(true);
                }
            }
        } catch (Throwable t) {
            logger.error("Cannot refresh the Instance Info ", t);
        }
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public String getASGName() {
        return this.asgName;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("[Application Info:\n");
        buf.append("\tname: ").append(getAppName()).
                append("\n\thostname: ").append(getHostName()).
                append("\n\tport: ").append(getPort()).
                append("\n\tipAddr: ").append(getIPAddr()).
                append("\n\tversion: ").append(getVersion()).
                append("\n\tdataCenterInfo: " + dataCenterInfo.toString());

        for (Iterator<Entry<String, String>> iter = metadata.entrySet().iterator();
                iter.hasNext();) {
            Entry<String, String> entry = iter.next();
            buf.append("\n\t").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        buf.append("\n]");
        return buf.toString();
    }

    //Currently NetflixInfo - only have static name so it's a constant currently
    public static class DefaultDataCenterInfo implements DataCenterInfo {

        public final static DefaultDataCenterInfo INSTANCE = new DefaultDataCenterInfo();

        private DefaultDataCenterInfo() {
        }
        private Name name = Name.Netflix;

        @Override
        public Name getName() {
            return name;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("[Netflix Info:\n");
            buf.append("\tname: ").append(getName()).append("\n]");
            return buf.toString();
        }
    }
    
    public enum ActionType {
        ADDED,
        MODIFIED,
        DELETED
    }
    
    public static String resolveDeploymentContextBasedVipAddresses(
            String vipAddressMacro) {
        String result = vipAddressMacro;

        if (vipAddressMacro == null) {
            return null;
        }

        Matcher matcher = VIP_ATTRIBUTES_PATTERN.matcher(result);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = DynamicPropertyFactory.getInstance().getStringProperty(key, null).get();
            
            logger.debug("att:" + matcher.group());
            logger.debug(", att key:" + key);
            logger.debug(", att value:" + value);
            logger.debug("");
            result = result.replaceAll("\\$\\{" + key + "\\}", value);
            matcher = VIP_ATTRIBUTES_PATTERN.matcher(result);
        }
        
        return result;
    }

    
}