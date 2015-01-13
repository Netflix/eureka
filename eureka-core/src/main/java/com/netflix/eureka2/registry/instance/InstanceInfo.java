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

package com.netflix.eureka2.registry.instance;

import com.netflix.eureka2.registry.datacenter.DataCenterInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * TODO: merge this and Delta into an abstract superclass that deal with id and version?
 * JavaBean for InstanceInfo.
 * @author David Liu
 */
public class InstanceInfo {

    protected final String id;

    protected String appGroup;
    protected String app;
    protected String asg;
    protected String vipAddress;
    protected String secureVipAddress;
    protected HashSet<ServicePort> ports;
    protected Status status;
    protected String homePageUrl;
    protected String statusPageUrl;
    protected HashSet<String> healthCheckUrls;
    protected Map<String, String> metaData;
    protected DataCenterInfo dataCenterInfo;

    // for serializers
    private InstanceInfo() {
        this(null);
    }

    protected InstanceInfo(String id) {
        this.id = id;
    }

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

    /**4
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
     * @return the port numbers that is used for servicing requests
     */
    public HashSet<ServicePort> getPorts() {
        return ports;
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

    public DataCenterInfo getDataCenterInfo() {
        return dataCenterInfo;
    }

    public Map<String, String> getMetaData() {
        return metaData == null ? null : Collections.unmodifiableMap(metaData);
    }

    public Iterator<ServiceEndpoint> serviceEndpoints() {
        return ServiceEndpoint.iteratorFrom(this);
    }

    // ------------------------------------------
    // Non-bean methods
    // ------------------------------------------


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InstanceInfo that = (InstanceInfo) o;

        if (app != null ? !app.equals(that.app) : that.app != null) {
            return false;
        }
        if (appGroup != null ? !appGroup.equals(that.appGroup) : that.appGroup != null) {
            return false;
        }
        if (asg != null ? !asg.equals(that.asg) : that.asg != null) {
            return false;
        }
        if (dataCenterInfo != null ? !dataCenterInfo.equals(that.dataCenterInfo) : that.dataCenterInfo != null) {
            return false;
        }
        if (healthCheckUrls != null ? !healthCheckUrls.equals(that.healthCheckUrls) : that.healthCheckUrls != null) {
            return false;
        }
        if (homePageUrl != null ? !homePageUrl.equals(that.homePageUrl) : that.homePageUrl != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (metaData != null ? !metaData.equals(that.metaData) : that.metaData != null) {
            return false;
        }
        if (ports != null ? !ports.equals(that.ports) : that.ports != null) {
            return false;
        }
        if (secureVipAddress != null ? !secureVipAddress.equals(that.secureVipAddress) : that.secureVipAddress != null) {
            return false;
        }
        if (status != that.status) {
            return false;
        }
        if (statusPageUrl != null ? !statusPageUrl.equals(that.statusPageUrl) : that.statusPageUrl != null) {
            return false;
        }
        if (vipAddress != null ? !vipAddress.equals(that.vipAddress) : that.vipAddress != null) {
            return false;
        }

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
        result = 31 * result + (ports != null ? ports.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (homePageUrl != null ? homePageUrl.hashCode() : 0);
        result = 31 * result + (statusPageUrl != null ? statusPageUrl.hashCode() : 0);
        result = 31 * result + (healthCheckUrls != null ? healthCheckUrls.hashCode() : 0);
        result = 31 * result + (metaData != null ? metaData.hashCode() : 0);
        result = 31 * result + (dataCenterInfo != null ? dataCenterInfo.hashCode() : 0);
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
                ", ports=" + ports +
                ", status=" + status +
                ", homePageUrl='" + homePageUrl + '\'' +
                ", statusPageUrl='" + statusPageUrl + '\'' +
                ", healthCheckUrls=" + healthCheckUrls +
                ", metaData=" + metaData +
                ", dataCenterInfo=" + dataCenterInfo +
                '}';
    }

    /**
     * Apply the delta instance to the current InstanceInfo
     *
     * @param delta the delta changes to applyTo
     * @return a new InstanceInfo with the deltas applied
     */
    public InstanceInfo applyDelta(Delta delta) {
        if (!id.equals(delta.getId())) {
            throw new UnsupportedOperationException("Cannot apply delta to instanceInfo with non-matching id: "
                    + delta.getId() + ", " + id);
        }

        InstanceInfo.Builder newInstanceInfoBuilder = new InstanceInfo.Builder().withInstanceInfo(this);
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
    public Set<Delta<?>> diffNewer(InstanceInfo another) {
        return diff(this, another);
    }

    /**
     * Diff the current instanceInfo with another "older" InstanceInfo, returning the results as a set of Deltas
     * iff the two instanceInfo have matching ids.
     *
     * @param another the "older" instanceInfo
     * @return the set of deltas, or null if the diff is invalid (InstanceInfos are null or id mismatch)
     */
    public Set<Delta<?>> diffOlder(InstanceInfo another) {
        return diff(another, this);
    }

    private static Set<Delta<?>> diff(InstanceInfo oldInstanceInfo, InstanceInfo newInstanceInfo) {
        if (oldInstanceInfo == null || newInstanceInfo == null) {
            return null;
        }

        if (!oldInstanceInfo.getId().equals(newInstanceInfo.getId())) {
            return null;
        }

        Set<Delta<?>> deltas = new HashSet<Delta<?>>();

        for (InstanceInfoField.Name fieldName : InstanceInfoField.Name.values()) {
            InstanceInfoField<Object> field = InstanceInfoField.forName(fieldName);
            Object oldValue = field.getValue(oldInstanceInfo);
            Object newValue = field.getValue(newInstanceInfo);

            if (!equalsNullable(oldValue, newValue)) {  // there is a difference
                Delta<?> delta = new Delta.Builder()
                        .withId(newInstanceInfo.getId())
                        .withDelta(field, newValue)
                        .build();
                deltas.add(delta);
            }
        }

        return deltas;
    }

    private static boolean equalsNullable(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null || b == null) {
            return false;
        } else {
            return a.equals(b);
        }
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
        private String id;

        private String appGroup;
        private String app;
        private String asg;
        private String vipAddress;
        private String secureVipAddress;
        private HashSet<ServicePort> ports;
        private Status status;
        private String homePageUrl;
        private String statusPageUrl;
        private HashSet<String> healthCheckUrls;
        private DataCenterInfo dataCenterInfo;
        private Map<String, String> metaData;

        public Builder withInstanceInfo(InstanceInfo another) {
            this.id = another.id;

            this.appGroup = another.appGroup;
            this.app = another.app;
            this.asg = another.asg;
            this.vipAddress = another.vipAddress;
            this.secureVipAddress = another.secureVipAddress;
            this.ports = another.ports;
            this.status = another.status;
            this.homePageUrl = another.homePageUrl;
            this.statusPageUrl = another.statusPageUrl;
            this.healthCheckUrls = another.healthCheckUrls;
            this.metaData = another.metaData;
            this.dataCenterInfo = another.dataCenterInfo;
            return this;
        }

        public Builder withBuilder(Builder another) {
            this.id = another.id == null ? this.id : another.id;

            this.appGroup = another.appGroup == null ? this.appGroup : another.appGroup;
            this.app = another.app == null ? this.app : another.app;
            this.asg = another.asg == null ? this.asg : another.asg;
            this.vipAddress = another.vipAddress == null ? this.vipAddress : another.vipAddress;
            this.secureVipAddress = another.secureVipAddress == null ? this.secureVipAddress : another.secureVipAddress;
            this.ports = another.ports == null ? this.ports : new HashSet<>(another.ports);
            this.status = another.status == null ? this.status : another.status;
            this.homePageUrl = another.homePageUrl == null ? this.homePageUrl : another.homePageUrl;
            this.statusPageUrl = another.statusPageUrl == null ? this.statusPageUrl : another.statusPageUrl;
            this.healthCheckUrls = another.healthCheckUrls == null ? this.healthCheckUrls : new HashSet<>(another.healthCheckUrls);
            this.metaData = another.metaData == null ? this.metaData : new HashMap<>(another.metaData);
            this.dataCenterInfo = another.dataCenterInfo == null ? this.dataCenterInfo : another.dataCenterInfo;
            return this;
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withAppGroup(String appGroup) {
            this.appGroup = appGroup;
            return this;
        }

        public Builder withApp(String app) {
            this.app = app;
            return this;
        }

        public Builder withAsg(String asg) {
            this.asg = asg;
            return this;
        }

        public Builder withVipAddress(String vipAddress) {
            this.vipAddress = vipAddress;
            return this;
        }

        public Builder withSecureVipAddress(String secureVipAddress) {
            this.secureVipAddress = secureVipAddress;
            return this;
        }

        public Builder withPorts(HashSet<ServicePort> ports) {
            if (ports == null || ports.isEmpty()) {
                this.ports = null;
            } else {
                this.ports = new HashSet<>(ports);
                this.ports.remove(null);
            }
            return this;
        }

        public Builder withPorts(ServicePort... ports) {
            if(ports == null || ports.length == 0) {
                this.ports = null;
            }
            this.ports = new HashSet<>();
            Collections.addAll(this.ports, ports);
            return this;
        }

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withHomePageUrl(String homePageUrl) {
            this.homePageUrl = homePageUrl;
            return this;
        }

        public Builder withStatusPageUrl(String statusPageUrl) {
            this.statusPageUrl = statusPageUrl;
            return this;
        }

        public Builder withHealthCheckUrls(HashSet<String> healthCheckUrls) {
            if (healthCheckUrls == null || healthCheckUrls.isEmpty()) {
                this.healthCheckUrls = null;
            } else {
                this.healthCheckUrls = new HashSet<>(healthCheckUrls);
                this.healthCheckUrls.remove(null); // Data cleanup
            }
            return this;
        }

        public Builder withMetaData(String key, String value) {
            if(metaData == null) {
                metaData = new HashMap<>();
            }
            metaData.put(key, value);
            return this;
        }

        public Builder withMetaData(Map<String, String> metaData) {
            this.metaData = metaData;
            return this;
        }

        public Builder withDataCenterInfo(DataCenterInfo location) {
            this.dataCenterInfo = location;
            return this;
        }

        public InstanceInfo build() {
            if (id == null) {
                throw new IllegalArgumentException("InstanceInfo id cannot be null");
            }
            InstanceInfo result = new InstanceInfo(this.id);
            result.appGroup = this.appGroup;
            result.app = this.app;
            result.asg = this.asg;
            result.vipAddress = this.vipAddress;
            result.secureVipAddress = this.secureVipAddress;
            result.ports = this.ports;
            result.status = this.status;
            result.homePageUrl = this.homePageUrl;
            result.statusPageUrl = this.statusPageUrl;
            result.healthCheckUrls = this.healthCheckUrls;
            result.metaData = metaData;
            result.dataCenterInfo = this.dataCenterInfo;

            return result;
        }
    }

    public static class ServiceEndpoint {

        private final NetworkAddress address;
        private final ServicePort servicePort;

        private ServiceEndpoint(NetworkAddress address, ServicePort servicePort) {
            this.address = address;
            this.servicePort = servicePort;
        }

        public NetworkAddress getAddress() {
            return address;
        }

        public ServicePort getServicePort() {
            return servicePort;
        }

        public static Iterator<ServiceEndpoint> iteratorFrom(final InstanceInfo instanceInfo) {
            final List<NetworkAddress> addresses = instanceInfo.getDataCenterInfo().getAddresses();
            final HashSet<ServicePort> ports = instanceInfo.getPorts();
            if (ports == null || ports.isEmpty() || addresses == null || addresses.isEmpty()) {
                return Collections.emptyIterator();
            }

            return new Iterator<ServiceEndpoint>() {
                private final Iterator<ServicePort> servicePortIt = ports.iterator();
                private ServicePort currentPort = servicePortIt.next();

                private Iterator<NetworkAddress> networkAddressIt = addresses.iterator();

                @Override
                public boolean hasNext() {
                    if (networkAddressIt.hasNext()) {
                        return true;
                    }
                    if (servicePortIt.hasNext()) {
                        currentPort = servicePortIt.next();
                        networkAddressIt = addresses.iterator();
                        return true;
                    }
                    return false;
                }

                @Override
                public ServiceEndpoint next() {
                    if (hasNext()) {
                        return new ServiceEndpoint(networkAddressIt.next(), currentPort);
                    }
                    throw new NoSuchElementException();
                }

                @Override
                public void remove() {
                    throw new IllegalStateException("Operation not supported");
                }
            };
        }
    }
}
