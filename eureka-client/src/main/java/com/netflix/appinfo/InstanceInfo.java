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
package com.netflix.appinfo;

import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.converters.Auto;
import com.netflix.discovery.provider.Serializer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * The class that holds information required for registration with
 * <tt>Eureka Server</tt> and to be discovered by other components.
 * <p>
 * <code>@Auto</code> annotated fields are serialized as is; Other fields are
 * serialized as specified by the <code>@Serializer</code>.
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
@Serializer("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("instance")
public class InstanceInfo {
    private static final Logger logger = LoggerFactory
    .getLogger(InstanceInfo.class);
    private static final Pattern VIP_ATTRIBUTES_PATTERN = Pattern
    .compile("\\$\\{(.*?)\\}");

    public final static int DEFAULT_PORT = 7001;
    public final static int DEFAULT_SECURE_PORT = 7002;
    public final static int DEFAULT_COUNTRY_ID = 1; // US
    private volatile String appName;
    private volatile String ipAddr;
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
    @XStreamOmitField
    private String statusPageRelativeUrl;
    @XStreamOmitField
    private String statusPageExplicitUrl;
    @XStreamOmitField
    private String healthCheckRelativeUrl;
    @XStreamOmitField
    private String healthCheckSecureExplicitUrl;
    @XStreamOmitField
    private String vipAddressUnresolved;
    @XStreamOmitField
    private String secureVipAddressUnresolved;
    @XStreamOmitField
    private String healthCheckExplicitUrl;
    @Deprecated
    private volatile int countryId = DEFAULT_COUNTRY_ID; // Defaults to US
    private volatile boolean isSecurePortEnabled = false;
    private volatile boolean isUnsecurePortEnabled = true;
    private volatile DataCenterInfo dataCenterInfo;
    private volatile String hostName;
    private volatile InstanceStatus status = InstanceStatus.UP;
    private volatile InstanceStatus overriddenstatus = InstanceStatus.UNKNOWN;
    @XStreamOmitField
    private volatile boolean isInstanceInfoDirty = false;
    private volatile LeaseInfo leaseInfo;
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
    private String version = "unknown";
    
    private InstanceInfo() {
    }

    public enum InstanceStatus {
        UP, // Ready to receive traffic
        DOWN, // Do not send traffic- healthcheck callback failed
        STARTING, // Just about starting- initializations to be done - do not
        // send traffic
        OUT_OF_SERVICE, // Intentionally shutdown for traffic
        UNKNOWN;

        public static InstanceStatus toEnum(String s) {
            for (InstanceStatus e : InstanceStatus.values()) {
                if (e.name().equalsIgnoreCase(s)) {
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
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
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

    public static final class Builder {
        private static final String COLON = ":";
        private static final String HTTPS_PROTOCOL = "https://";
        private static final String HTTP_PROTOCOL = "http://";

        @XStreamOmitField
        private InstanceInfo result;
        
        private String namespace;

        private Builder() {
            result = new InstanceInfo();
        }

        public Builder(InstanceInfo instanceInfo) {
            result = instanceInfo;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Set the application name of the instance.This is mostly used in
         * querying of instances.
         * 
         * @param appName
         *            the application name.
         * @return the instance info builder.
         */
        public Builder setAppName(String appName) {
            result.appName = appName;
            if (result.appName != null) {
                result.appName = result.appName.toUpperCase();
            }
            return this;
        }

        /**
         * Sets the fully qualified hostname of this running instance.This is
         * mostly used in constructing the {@link URL} for communicating with
         * the instance.
         * 
         * @param hostName
         *            the host name of the instance.
         * @return the {@link InstanceInfo} builder.
         */
        public Builder setHostName(String hostName) {
            String existingHostName = result.hostName;
            result.hostName = hostName;
            if ((existingHostName != null)
                    && !(hostName.equals(existingHostName))) {
                refreshStatusPageUrl().refreshHealthCheckUrl()
                .refreshVIPAddress().refreshSecureVIPAddress();
            }
            return this;
        }

        /**
         * Sets the status of the instances.If the status is UP, that is when
         * the instance is ready to service requests.
         * 
         * @param status
         *            the {@link InstanceStatus} of the instance.
         * @return the {@link InstanceInfo} builder.
         */
        public Builder setStatus(InstanceStatus status) {
            result.status = status;
            return this;
        }

        /**
         * Sets the status overridden by some other external process.This is
         * mostly used in putting an instance out of service to block traffic to
         * it.
         * 
         * @param status
         *            the overridden {@link InstanceStatus} of the instance.
         * @return @return the {@link InstanceInfo} builder.
         */
        public Builder setOverriddenStatus(InstanceStatus status) {
            result.overriddenstatus = status;
            return this;
        }

        /**
         * Sets the ip address of this running instance.
         * 
         * @param ip
         *            the ip address of the instance.
         * @return the {@link InstanceInfo} builder.
         */
        public Builder setIPAddr(String ip) {
            result.ipAddr = ip;
            return this;
        }

        /**
         * Sets the identity of this application instance.
         * 
         * @param sid
         *            the sid of the instance.
         * @return the {@link InstanceInfo} builder.
         */
        @Deprecated
        public Builder setSID(String sid) {
            result.sid = sid;
            return this;
        }

        /**
         * Sets the port number that is used to service requests.
         * 
         * @param port
         *            the port number that is used to service requests.
         * @return the {@link InstanceInfo} builder.
         */
        public Builder setPort(int port) {
            result.port = port;
            return this;
        }

        /**
         * Sets the secure port that is used to service requests.
         * 
         * @param port
         *            the secure port that is used to service requests.
         * @return the {@link InstanceInfo} builder.
         */
        public Builder setSecurePort(int port) {
            result.securePort = port;
            return this;
        }

        /**
         * Enabled or disable secure/non-secure ports .
         * 
         * @param type
         *            Secure or Non-Secure.
         * @param isEnabled
         *            true if enabled.
         * @return the instance builder.
         */
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
         * Sets the absolute home page {@link URL} for this instance. The users
         * can provide the <code>homePageUrlPath</code> if the home page resides
         * in the same instance talking to discovery, else in the cases where
         * the instance is a proxy for some other server, it can provide the
         * full {@link URL}. If the full {@link URL} is provided it takes
         * precedence.
         * 
         * <p>
         * The full {@link URL} should follow the format
         * http://${netflix.appinfo.hostname}:7001/ where the value
         * ${netflix.appinfo.hostname} is replaced at runtime.
         * </p>
         * 
         * @param relativeUrl
         *            the relative url path of the home page.
         * 
         * @param explicitUrl
         *            - The full {@link URL} for the home page
         * @return the instance builder.
         */
        public Builder setHomePageUrl(String relativeUrl, String explicitUrl) {
            String hostNameInterpolationExpression = "${" + namespace + "hostname}";
            if (explicitUrl != null) {
                result.homePageUrl = explicitUrl.replace(
                        hostNameInterpolationExpression, result.hostName);
            } else if (relativeUrl != null) {
                result.homePageUrl = HTTP_PROTOCOL + result.hostName + COLON
                + result.port + relativeUrl;
            }
            return this;
        }

        /**
         * Sets the absolute status page {@link URL} for this instance. The
         * users can provide the <code>statusPageUrlPath</code> if the status
         * page resides in the same instance talking to discovery, else in the
         * cases where the instance is a proxy for some other server, it can
         * provide the full {@link URL}. If the full {@link URL} is provided it
         * takes precedence.
         * 
         * <p>
         * The full {@link URL} should follow the format
         * http://${netflix.appinfo.hostname}:7001/Status where the value
         * ${netflix.appinfo.hostname} is replaced at runtime.
         * </p>
         * 
         * @param relativeUrl
         *            - The {@link URL} path for status page for this instance
         * @param explicitUrl
         *            - The full {@link URL} for the status page
         * @return - Builder instance
         */
        public Builder setStatusPageUrl(String relativeUrl, String explicitUrl) {
            String hostNameInterpolationExpression = "${" + namespace + "hostname}";
            result.statusPageRelativeUrl = relativeUrl;
            result.statusPageExplicitUrl = explicitUrl;
            if (explicitUrl != null) {
                result.statusPageUrl = explicitUrl.replace(
                        hostNameInterpolationExpression, result.hostName);
            } else if (relativeUrl != null) {
                result.statusPageUrl = HTTP_PROTOCOL + result.hostName + COLON
                + result.port + relativeUrl;
            }
            return this;
        }

        /**
         * Sets the absolute health check {@link URL} for this instance for both
         * secure and non-secure communication The users can provide the
         * <code>healthCheckUrlPath</code> if the healthcheck page resides in
         * the same instance talking to discovery, else in the cases where the
         * instance is a proxy for some other server, it can provide the full
         * {@link URL}. If the full {@link URL} is provided it takes precedence.
         * 
         * <p>
         * The full {@link URL} should follow the format
         * http://${netflix.appinfo.hostname}:7001/healthcheck where the value
         * ${netflix.appinfo.hostname} is replaced at runtime.
         * </p>
         * 
         * @param relativeUrl
         *            - The {@link URL} path for healthcheck page for this
         *            instance.
         * @param explicitUrl
         *            - The full {@link URL} for the healthcheck page.
         * @param secureExplicitUrl
         *            the full secure explicit url of the healthcheck page.
         * @return the instance builder
         */
        public Builder setHealthCheckUrls(String relativeUrl,
                String explicitUrl, String secureExplicitUrl) {
            String hostNameInterpolationExpression = "${" + namespace + "hostname}";
            result.healthCheckRelativeUrl = relativeUrl;
            result.healthCheckExplicitUrl = explicitUrl;
            result.healthCheckSecureExplicitUrl = secureExplicitUrl;
            if (explicitUrl != null) {
                result.healthCheckUrl = explicitUrl.replace(
                        hostNameInterpolationExpression, result.hostName);
            } else if (result.isUnsecurePortEnabled) {
                result.healthCheckUrl = HTTP_PROTOCOL + result.hostName + COLON
                + result.port + relativeUrl;
            }

            if (secureExplicitUrl != null) {
                result.secureHealthCheckUrl = secureExplicitUrl.replace(
                        hostNameInterpolationExpression, result.hostName);
            } else if (result.isSecurePortEnabled) {
                result.secureHealthCheckUrl = HTTPS_PROTOCOL + result.hostName
                + COLON + result.securePort + relativeUrl;
            }
            return this;
        }

        /**
         * Sets the Virtual Internet Protocol address for this instance. The
         * address should follow the format <code><hostname:port></code> This
         * address needs to be resolved into a real address for communicating
         * with this instance.
         * 
         * @param vipAddress
         *            - The Virtual Internet Protocol address of this instance.
         * @return the instance builder.
         */
        public Builder setVIPAddress(String vipAddress) {
            result.vipAddressUnresolved = vipAddress;
            result.vipAddress = resolveDeploymentContextBasedVipAddresses(vipAddress);
            return this;
        }

        /**
         * Sets the Secure Virtual Internet Protocol address for this instance.
         * The address should follow the format <hostname:port> This address
         * needs to be resolved into a real address for communicating with this
         * instance.
         * 
         * @param secureVIPAddress
         *            the secure VIP address of the instance.
         * 
         * @return - Builder instance
         */
        public Builder setSecureVIPAddress(String secureVIPAddress) {
            result.secureVipAddressUnresolved = secureVIPAddress;
            result.secureVipAddress = resolveDeploymentContextBasedVipAddresses(secureVIPAddress);
            return this;
        }

        /**
         * Sets the datacenter information.
         * 
         * @param datacenter
         *            the datacenter information for where this instance is
         *            running.
         * @return the {@link InstanceInfo} builder.
         */
        public Builder setDataCenterInfo(DataCenterInfo datacenter) {
            result.dataCenterInfo = datacenter;
            return this;
        }

        /**
         * Set the instance lease information.
         * 
         * @param info
         *            the lease information for this instance.
         */
        public void setLeaseInfo(LeaseInfo info) {
            result.leaseInfo = info;
        }

        /**
         * Add arbitrary metadata to running instance
         * 
         * @param key
         *            The key of the metadata.
         * @param val
         *            The value of the metadata.
         * @return the {@link InstanceInfo} builder.
         */
        public Builder add(String key, String val) {
            result.metadata.put(key, val);
            return this;
        }

        /**
         * Replace the existing metadata map with a new one.
         * 
         * @param mt
         *            the new metadata name.
         * @return instance info builder.
         */
        public Builder setMetadata(Map<String, String> mt) {
            result.metadata = mt;
            return this;
        }

        /**
         * Returns the encapsulated instance info even it it is not built fully.
         * 
         * @return the existing information about the instance.
         */
        public InstanceInfo getRawInstance() {
            return result;
        }

        /**
         * Build the {@link InstanceInfo} object.
         * 
         * @return the {@link InstanceInfo} that was built based on the
         *         information supplied.
         */
        public InstanceInfo build() {
            if (!isInitialized()) {
                throw new IllegalStateException("name is required!");
            }
            return result;
        }

        public boolean isInitialized() {
            return (result.appName != null);
        }

        /**
         * Sets the AWS ASG name for this instance.
         * 
         * @param asgName
         *            the asg name for this instance.
         * @return the instance info builder.
         */
        public Builder setASGName(String asgName) {
            result.asgName = asgName;
            return this;
        }

        private Builder refreshStatusPageUrl() {
            setStatusPageUrl(result.statusPageRelativeUrl,
                    result.statusPageExplicitUrl);
            return this;
        }

        private Builder refreshHealthCheckUrl() {
            setHealthCheckUrls(result.healthCheckRelativeUrl,
                    result.healthCheckExplicitUrl,
                    result.healthCheckSecureExplicitUrl);
            return this;
        }

        private Builder refreshSecureVIPAddress() {
            setSecureVIPAddress(result.secureVipAddressUnresolved);
            return this;
        }

        private Builder refreshVIPAddress() {
            setVIPAddress(result.vipAddressUnresolved);
            return this;
        }

        public Builder setNamespace(String namespace) {
                this.namespace = namespace;
                return this;
        }
    }

    /**
     * Return the name of the application registering with discovery.
     * 
     * @return the string denoting the application name.
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Returns the fully qualified hostname of this running instance
     * 
     * @return the hostname.
     */
    public String getHostName() {
        return hostName;
    }

    @Deprecated
    public void setSID(String sid) {
        this.sid = sid;
        setIsDirty(true);
    }

    @Deprecated
    public String getSID() {
        return sid;
    }

    /**
     * 
     * Returns the unique id of the instance.
     * 
     * @return the unique id.
     */
    public String getId() {
        if (dataCenterInfo.getName() == Name.Amazon) {
            return ((AmazonInfo) dataCenterInfo).get(MetaDataKey.instanceId);
        } else {
            return hostName;
        }
    }

    /**
     * Returns the ip address of the instance.
     * 
     * @return - the ip address, in AWS scenario it is a private IP.
     */
    public String getIPAddr() {
        return ipAddr;
    }

    /**
     * 
     * Returns the port number that is used for servicing requests.
     * 
     * @return - the non-secure port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the status of the instance.
     * 
     * @return the status indicating whether the instance can handle requests.
     */
    public InstanceStatus getStatus() {
        return status;
    }

    /**
     * Returns the overridden status if any of the instance.
     * 
     * @return the status indicating whether an external process has changed the
     *         status.
     */
    public InstanceStatus getOverriddenStatus() {
        return overriddenstatus;
    }

    /**
     * Returns data center information identifying if it is AWS or not.
     * 
     * @return the data center information.
     */
    public DataCenterInfo getDataCenterInfo() {
        return dataCenterInfo;
    }

    /**
     * Returns the lease information regarding when it expires.
     * 
     * @return the lease information of this instance.
     */
    public LeaseInfo getLeaseInfo() {
        return leaseInfo;
    }

    /**
     * Sets the lease information regarding when it expires.
     * 
     * @param info
     *            the lease information of this instance.
     */
    public void setLeaseInfo(LeaseInfo info) {
        leaseInfo = info;
    }

    /**
     * Returns all application specific metadata set on the instance.
     * 
     * @return application specific metadata.
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Deprecated
    public int getCountryId() {
        return countryId;
    }

    /**
     * Returns the secure port that is used for servicing requests.
     * 
     * @return the secure port.
     */
    public int getSecurePort() {
        return securePort;
    }

    /**
     * Checks whether a port is enabled for traffic or not.
     * 
     * @param type
     *            indicates whether it is secure or non-secure port.
     * @return true if the port is enabled, false otherwise.
     */
    public boolean isPortEnabled(PortType type) {
        if (type == PortType.SECURE) {
            return isSecurePortEnabled;
        } else {
            return isUnsecurePortEnabled;
        }
    }

    /**
     * Returns the time elapsed since epoch since the instance status has been
     * last updated.
     * 
     * @return the time elapsed since epoch since the instance has been last
     *         updated.
     */
    public long getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    /**
     * Set the update time for this instance when the status was update.
     */
    public void setLastUpdatedTimestamp() {
        this.lastUpdatedTimestamp = System.currentTimeMillis();
    }

    /**
     * Gets the home page {@link URL} set for this instance.
     * 
     * @return home page {@link URL}
     */
    public String getHomePageUrl() {
        return homePageUrl;
    }

    /**
     * Gets the status page {@link URL} set for this instance.
     * 
     * @return status page {@link URL}
     */
    public String getStatusPageUrl() {
        return statusPageUrl;
    }

    /**
     * Gets the absolute URLs for the health check page for both secure and
     * non-secure protocols. If the port is not enabled then the URL is
     * excluded.
     * 
     * @return A Set containing the string representation of health check urls
     *         for secure and non secure protocols
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
     * 
     * @return - The Virtual Internet Protocol address
     */
    public String getVIPAddress() {
        return vipAddress;
    }

    /**
     * Get the Secure Virtual Internet Protocol address for this instance.
     * Defaults to hostname if not specified.
     * 
     * @return - The Secure Virtual Internet Protocol address.
     */
    public String getSecureVipAddress() {
        return secureVipAddress;
    }

    /**
     * Gets the last time stamp when this instance was touched.
     * 
     * @return last timestamp when this instance was touched.
     */
    public Long getLastDirtyTimestamp() {
        return lastDirtyTimestamp;
    }

    /**
     * Set the time indicating that the instance was touched.
     * 
     * @param lastDirtyTimestamp
     *            time when the instance was touched.
     */
    public void setLastDirtyTimestamp(Long lastDirtyTimestamp) {
        this.lastDirtyTimestamp = lastDirtyTimestamp;
    }

    /**
     * Set the status for this instance.
     * 
     * @param status
     *            status for this instance.
     */
    public synchronized void setStatus(InstanceStatus status) {
        if (this.status != status) {
            this.status = status;
            setIsDirty(true);
        }
    }

    /**
     * Set the status for this instance without updating the dirty timestamp.
     * 
     * @param status
     *            status for this instance.
     */
    public synchronized void setStatusWithoutDirty(InstanceStatus status) {
        if (this.status != status) {
            this.status = status;
        }
    }

    /**
     * Sets the overridden status for this instance.Normally set by an external
     * process to disable instance from taking traffic.
     * 
     * @param status
     *            overridden status for this instance.
     */
    public synchronized void setOverriddenStatus(InstanceStatus status) {
        if (this.overriddenstatus != status) {
            this.overriddenstatus = status;
        }
    }

    /**
     * Returns whether any state changed so that {@link DiscoveryClient} can
     * check whether to retransmit info or not on the next heartbeat.
     * 
     * @return true if the {@link InstanceInfo} is dirty, false otherwise.
     */
    public boolean isDirty() {
        return isInstanceInfoDirty;
    }

    /**
     * Sets the dirty flag so that the instance information can be carried to
     * the discovery server on the next heartbeat.
     * 
     * @param b
     *            - true if dirty, false otherwise.
     */
    public void setIsDirty(boolean b) {
        isInstanceInfoDirty = b;
        this.lastDirtyTimestamp = System.currentTimeMillis();
    }

    /**
     * Sets a flag if this instance is the same as the discovery server that is
     * return the instances. This flag is used by the discovery clients to
     * identity the discovery server which is coordinating/returning the
     * information.
     */
    public void setIsCoordinatingDiscoveryServer() {
        String instanceId = getId();
        if ((instanceId != null)
                && (instanceId.equals(ApplicationInfoManager.getInstance()
                        .getInfo().getId()))) {
            isCoordinatingDiscoveryServer = Boolean.TRUE;
        } else {
            isCoordinatingDiscoveryServer = Boolean.FALSE;
        }
    }

    /**
     * Finds if this instance is the coordinating discovery server.
     * 
     * @return - true, if this instance is the coordinating discovery server,
     *         false otherwise.
     */
    public Boolean isCoordinatingDiscoveryServer() {
        return isCoordinatingDiscoveryServer;
    }

    /**
     * Returns the type of action done on the instance in the server.Primarily
     * used for updating deltas in the {@link DiscoveryClient}
     * instance.
     * 
     * @return action type done on the instance.
     */
    public ActionType getActionType() {
        return actionType;
    }

    /**
     * Set the action type performed on this instance in the server.
     * 
     * @param actionType
     *            action type done on the instance.
     */
    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    /**
     * Get AWS autoscaling group name if any.
     * 
     * @return autoscaling group name of this instance.
     */
    public String getASGName() {
        return this.asgName;
    }

    /**
     * Returns the specification version of this application
     * 
     * @return the string indicating the version of the application.
     */
    @Deprecated
    public String getVersion() {
        return version;
    }

    public enum ActionType {
        ADDED, // Added in the discovery server
        MODIFIED, // Changed in the discovery server
        DELETED
        // Deleted from the discovery server
    }

    /**
     * Register application specific metadata to be sent to the discovery
     * server.
     * 
     * @param runtimeMetadata
     *            Map containing key/value pairs.
     */
    synchronized void registerRuntimeMetadata(
            Map<String, String> runtimeMetadata) {
        metadata.putAll(runtimeMetadata);
        setIsDirty(true);
    }

    /**
     * Convert <code>VIPAddress</code> by substituting environment variables if
     * necessary.
     * 
     * @param vipAddressMacro
     *            the macro for which the interpolation needs to be made.
     * @return a string representing the final <code>VIPAddress</code> after
     *         substitution.
     */
    private static String resolveDeploymentContextBasedVipAddresses(
            String vipAddressMacro) {
        String result = vipAddressMacro;

        if (vipAddressMacro == null) {
            return null;
        }

        Matcher matcher = VIP_ATTRIBUTES_PATTERN.matcher(result);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = DynamicPropertyFactory.getInstance()
            .getStringProperty(key, "").get();

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