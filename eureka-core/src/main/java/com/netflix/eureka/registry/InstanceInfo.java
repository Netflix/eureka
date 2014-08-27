/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * TODO: merge this and Delta into an abstract superclass that deal with id and version?
 * JavaBean for InstanceInfo.
 * @author David Liu
 */
public class InstanceInfo {

    protected String id;
    protected String appGroup;
    protected String app;
    protected String asg;
    protected String vipAddress;
    protected String secureVipAddress;
    protected String hostname;
    protected String ip;
    protected HashSet<Integer> ports;
    protected HashSet<Integer> securePorts;
    protected Status status;
    protected String homePageUrl;
    protected String statusPageUrl;
    protected HashSet<String> healthCheckUrls;
    protected Long version;
    protected InstanceLocation instanceLocation;

    // for serializers
    private InstanceInfo() {}

    /**
     * @return unique identifier of this instance
     */
    public String getId() {
        return id;
    }

    /**
     * @return the appgroup this instance belong to
     */
    public String getAppGroup() {
        return appGroup;
    }

    /**
     * @return the application this instance belong to
     */
    public String getApp() {
        return app;
    }

    /**
     * @return the asg this instance belong to
     */
    public String getAsg() {
        return asg;
    }

    /**
     * @return the vip addresses of this instance
     */
    public String getVipAddress() {
        return vipAddress;
    }

    /**
     * @return the secure vip address of this instance
     */
    public String getSecureVipAddress() {
        return secureVipAddress;
    }

    /**
     * @return the hostname of this instance
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @return ip address of this instance
     */
    public String getIp() {
        return ip;
    }

    /**
     * @return the port numbers that is used for servicing requests
     */
    public HashSet<Integer> getPorts() {
        return ports;
    }

    /**
     * @return the secure port numbers that is used for servicing requests
     */
    public HashSet<Integer> getSecurePorts() {
        return securePorts;
    }

    /**
     * @return the current status of this instance
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return home page {@link java.net.URL}
     */
    public String getHomePageUrl() {
        return homePageUrl;
    }

    /**
     * @return status page {@link java.net.URL}
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
    public HashSet<String> getHealthCheckUrls() {
        return healthCheckUrls;
    }

    /**
     * All InstanceInfo instances contain a version string that can be used to determine timeline ordering
     * for InstanceInfo records with the same id.
     *
     * @return the version string for this InstanceInfo
     */
    public Long getVersion() {
        return version;
    }

    public InstanceLocation getInstanceLocation() {
        return instanceLocation;
    }

    // ------------------------------------------
    // Non-bean methods
    // ------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstanceInfo)) return false;

        InstanceInfo that = (InstanceInfo) o;

        if (app != null ? !app.equals(that.app) : that.app != null) return false;
        if (appGroup != null ? !appGroup.equals(that.appGroup) : that.appGroup != null) return false;
        if (asg != null ? !asg.equals(that.asg) : that.asg != null) return false;
        if (healthCheckUrls != null ? !healthCheckUrls.equals(that.healthCheckUrls) : that.healthCheckUrls != null)
            return false;
        if (homePageUrl != null ? !homePageUrl.equals(that.homePageUrl) : that.homePageUrl != null) return false;
        if (hostname != null ? !hostname.equals(that.hostname) : that.hostname != null) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (instanceLocation != null ? !instanceLocation.equals(that.instanceLocation) : that.instanceLocation != null)
            return false;
        if (ip != null ? !ip.equals(that.ip) : that.ip != null) return false;
        if (ports != null ? !ports.equals(that.ports) : that.ports != null) return false;
        if (securePorts != null ? !securePorts.equals(that.securePorts) : that.securePorts != null) return false;
        if (secureVipAddress != null ? !secureVipAddress.equals(that.secureVipAddress) : that.secureVipAddress != null)
            return false;
        if (status != that.status) return false;
        if (statusPageUrl != null ? !statusPageUrl.equals(that.statusPageUrl) : that.statusPageUrl != null)
            return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;
        if (vipAddress != null ? !vipAddress.equals(that.vipAddress) : that.vipAddress != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (appGroup != null ? appGroup.hashCode() : 0);
        result = 31 * result + (app != null ? app.hashCode() : 0);
        result = 31 * result + (asg != null ? asg.hashCode() : 0);
        result = 31 * result + (vipAddress != null ? vipAddress.hashCode() : 0);
        result = 31 * result + (secureVipAddress != null ? secureVipAddress.hashCode() : 0);
        result = 31 * result + (hostname != null ? hostname.hashCode() : 0);
        result = 31 * result + (ip != null ? ip.hashCode() : 0);
        result = 31 * result + (ports != null ? ports.hashCode() : 0);
        result = 31 * result + (securePorts != null ? securePorts.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (homePageUrl != null ? homePageUrl.hashCode() : 0);
        result = 31 * result + (statusPageUrl != null ? statusPageUrl.hashCode() : 0);
        result = 31 * result + (healthCheckUrls != null ? healthCheckUrls.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (instanceLocation != null ? instanceLocation.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "InstanceInfo{" +
                "id='" + id + '\'' +
                ", appGroup='" + appGroup + '\'' +
                ", app='" + app + '\'' +
                ", asg='" + asg + '\'' +
                ", vipAddress='" + vipAddress + '\'' +
                ", secureVipAddress='" + secureVipAddress + '\'' +
                ", hostname='" + hostname + '\'' +
                ", ip='" + ip + '\'' +
                ", ports=" + ports +
                ", securePorts=" + securePorts +
                ", status=" + status +
                ", homePageUrl='" + homePageUrl + '\'' +
                ", statusPageUrl='" + statusPageUrl + '\'' +
                ", healthCheckUrls=" + healthCheckUrls +
                ", version=" + version +
                ", instanceLocation=" + instanceLocation +
                '}';
    }

    /**
     * Apply the delta info to the current InstanceInfo
     *
     * @param delta the delta changes to applyTo
     * @return a new InstanceInfo with the deltas applied
     */
    public InstanceInfo applyDelta(Delta delta) throws Exception {
        if (!id.equals(delta.getId())) {
            throw new UnsupportedOperationException("Cannot apply delta to instanceInfo with non-matching id: "
                    + delta.getId() + ", " + id);
        }

        InstanceInfo.Builder newInstanceInfoBuilder = new InstanceInfo.Builder().withInstanceInfo(this);
        newInstanceInfoBuilder.withVersion(delta.getVersion());
        return delta.applyTo(newInstanceInfoBuilder).build();
    }

    /**
     * Diff the current instanceInfo with another "newer" InstanceInfo, returning the results as a set of Deltas
     * iff the two instanceInfo have matching ids.
     * The version of the delta will be the version of the other ("newer") instanceInfo
     *
     * @param another the "newer" instanceInfo
     * @return the set of deltas, or null if the diff is invalid (InstanceInfos are null or id mismatch)
     */
    public Set<Delta<?>> diffNewer(InstanceInfo another) throws Exception {
        return diff(this, another);
    }

    /**
     * Diff the current instanceInfo with another "older" InstanceInfo, returning the results as a set of Deltas
     * iff the two instanceInfo have matching ids.
     * The version of the delta will be the version of the current ("newer") instanceInfo
     *
     * @param another the "older" instanceInfo
     * @return the set of deltas, or null if the diff is invalid (InstanceInfos are null or id mismatch)
     */
    public Set<Delta<?>> diffOlder(InstanceInfo another) throws Exception {
        return diff(another, this);
    }

    private static Set<Delta<?>> diff(InstanceInfo oldInstanceInfo, InstanceInfo newInstanceInfo) throws Exception {
        if (oldInstanceInfo == null || newInstanceInfo == null) {
            return null;
        }

        if (!oldInstanceInfo.getId().equals(newInstanceInfo.getId())) {
            return null;
        }

        Set<Delta<?>> deltas = new HashSet<Delta<?>>();

        Long newVersion = newInstanceInfo.getVersion();
        for (InstanceInfoField field : InstanceInfoField.FIELD_MAP.values()) {
            Object oldValue = field.getValue(oldInstanceInfo);
            Object newValue = field.getValue(newInstanceInfo);

            if (oldValue == null || !oldValue.equals(newValue)) {  // there is a difference
                Delta<?> delta = new Delta.Builder()
                        .withId(newInstanceInfo.getId())
                        .withVersion(newVersion)
                        .withDelta(field, newValue)
                        .build();
                deltas.add(delta);
            }
        }

        return deltas;
    }


    // ------------------------------------------
    // Instance Status
    // ------------------------------------------

    public enum Status {
        UP,             // Ready for traffic
        DOWN,           // Not ready for traffic - healthcheck failure
        STARTING,       // Not ready for traffic - still initialising
        OUT_OF_SERVICE, // Not ready for traffic - user initiated operation
        UNKNOWN;

        public static Status toEnum(String s) {
            for (Status e : Status.values()) {
                if (e.name().equalsIgnoreCase(s)) {
                    return e;
                }
            }
            return UNKNOWN;
        }
    }

    // ------------------------------------------
    // Builder
    // ------------------------------------------

    public static final class Builder {
        private static final Logger logger = LoggerFactory.getLogger(InstanceInfo.Builder.class);

        private InstanceInfo info;

        public Builder() {
            info = new InstanceInfo();
        }

        public Builder withInstanceInfo(InstanceInfo another) {
            info.id = another.id;
            info.appGroup = another.appGroup;
            info.app = another.app;
            info.asg = another.asg;
            info.vipAddress = another.vipAddress;
            info.secureVipAddress = another.secureVipAddress;
            info.hostname = another.hostname;
            info.ip = another.ip;
            info.ports = another.ports;
            info.securePorts = another.securePorts;
            info.status = another.status;
            info.homePageUrl = another.homePageUrl;
            info.statusPageUrl = another.statusPageUrl;
            info.healthCheckUrls = another.healthCheckUrls;
            info.version = another.version;
            info.instanceLocation = another.instanceLocation;
            return this;
        }

        public Builder withId(String id) {
            info.id = id;
            return this;
        }

        protected Builder withVersion(Long version) {
            info.version = version;
            return this;
        }

        public Builder withAppGroup(String appGroup) {
            info.appGroup = appGroup;
            return this;
        }

        public Builder withApp(String app) {
            info.app = app;
            return this;
        }

        public Builder withAsg(String asg) {
            info.asg = asg;
            return this;
        }

        public Builder withVipAddress(String vipAddress) {
            info.vipAddress = vipAddress;
            return this;
        }

        public Builder withSecureVipAddress(String secureVipAddress) {
            info.secureVipAddress = secureVipAddress;
            return this;
        }

        public Builder withHostname(String hostname) {
            info.hostname = hostname;
            return this;
        }

        public Builder withIp(String ip) {
            info.ip = ip;
            return this;
        }

        public Builder withPorts(HashSet<Integer> ports) {
            info.ports = ports;
            return this;
        }

        public Builder withSecurePorts(HashSet<Integer> securePorts) {
            info.securePorts = securePorts;
            return this;
        }

        public Builder withStatus(Status status) {
            info.status = status;
            return this;
        }

        public Builder withHomePageUrl(String homePageUrl) {
            info.homePageUrl = homePageUrl;
            return this;
        }

        public Builder withStatusPageUrl(String statusPageUrl) {
            info.statusPageUrl = statusPageUrl;
            return this;
        }

        public Builder withHealthCheckUrls(HashSet<String> healthCheckUrls) {
            info.healthCheckUrls = healthCheckUrls;
            return this;
        }

        public Builder withInstanceLocation(InstanceLocation location) {
            info.instanceLocation = location;
            return this;
        }

        public InstanceInfo build() {
            // validate and sanitize
            if (info.version == null) {
                info.version = System.currentTimeMillis();
            }
            return info;
        }
    }
}
