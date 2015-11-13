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

package com.netflix.eureka2.model.instance;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;

/**
 * JavaBean for InstanceInfo.
 * @author David Liu
 */
public class StdInstanceInfo implements InstanceInfo {

    protected final String id;

    protected String appGroup;
    protected String app;
    protected String asg;
    protected String vipAddress;
    protected String secureVipAddress;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    protected HashSet<ServicePort> ports;

    protected Status status;
    protected String homePageUrl;
    protected String statusPageUrl;
    protected HashSet<String> healthCheckUrls;
    protected Map<String, String> metaData;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    protected DataCenterInfo dataCenterInfo;

    // for serializers
    private StdInstanceInfo() {
        this(null);
    }

    protected StdInstanceInfo(String id) {
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
    public Set<ServicePort> getPorts() {
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
        return ServiceEndpointImpl.iteratorFrom(this);
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

        StdInstanceInfo that = (StdInstanceInfo) o;

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

    // ------------------------------------------
    // Builder
    // ------------------------------------------

    public static Builder anInstanceInfo() {
        return new Builder();
    }

    public static final class Builder extends InstanceInfoBuilder {


        public StdInstanceInfo build() {
            if (id == null) {
                throw new IllegalArgumentException("InstanceInfo id cannot be null");
            }
            StdInstanceInfo result = new StdInstanceInfo(this.id);
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

    public static class ServiceEndpointImpl implements ServiceEndpoint {

        private final NetworkAddress address;
        private final ServicePort servicePort;

        private ServiceEndpointImpl(NetworkAddress address, ServicePort servicePort) {
            this.address = address;
            this.servicePort = servicePort;
        }

        public NetworkAddress getAddress() {
            return address;
        }

        public ServicePort getServicePort() {
            return servicePort;
        }

        public static Iterator<ServiceEndpoint> iteratorFrom(final StdInstanceInfo instanceInfo) {
            final List<NetworkAddress> addresses = instanceInfo.getDataCenterInfo().getAddresses();
            final HashSet<ServicePort> ports = (HashSet<ServicePort>) instanceInfo.getPorts();
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
                public ServiceEndpointImpl next() {
                    if (hasNext()) {
                        return new ServiceEndpointImpl(networkAddressIt.next(), currentPort);
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
