/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.ext.grpc.model.instance;

import java.util.*;

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.*;

/**
 */
public class GrpcInstanceInfoWrapper implements InstanceInfo, GrpcObjectWrapper<Eureka2.GrpcInstanceInfo> {

    private final Eureka2.GrpcInstanceInfo grpcInstanceInfo;

    private volatile Set<ServicePort> ports;
    private volatile HashSet<String> healthCheckUrls;
    private volatile DataCenterInfo dataCenterInfo;

    private GrpcInstanceInfoWrapper(Eureka2.GrpcInstanceInfo grpcInstanceInfo) {
        this.grpcInstanceInfo = grpcInstanceInfo;
    }

    @Override
    public String getId() {
        return grpcInstanceInfo.getId();
    }

    @Override
    public String getAppGroup() {
        return grpcInstanceInfo.getAppGroup();
    }

    @Override
    public String getApp() {
        return grpcInstanceInfo.getApp();
    }

    @Override
    public String getAsg() {
        return grpcInstanceInfo.getAsg();
    }

    @Override
    public String getVipAddress() {
        return grpcInstanceInfo.getVipAddress();
    }

    @Override
    public String getSecureVipAddress() {
        return grpcInstanceInfo.getSecureVipAddress();
    }

    @Override
    public Set<ServicePort> getPorts() {
        if (ports != null || grpcInstanceInfo.getPortsList() == null) {
            return ports;
        }
        Set<ServicePort> ports = new HashSet<>(grpcInstanceInfo.getPortsList().size());
        for (Eureka2.GrpcServicePort grpcPort : grpcInstanceInfo.getPortsList()) {
            ports.add(GrpcServicePortWrapper.asServicePort(grpcPort));
        }
        this.ports = ports;
        return ports;
    }

    @Override
    public Status getStatus() {
        return toStatus(grpcInstanceInfo.getStatus());
    }

    @Override
    public String getHomePageUrl() {
        return grpcInstanceInfo.getHomePageUrl();
    }

    @Override
    public String getStatusPageUrl() {
        return grpcInstanceInfo.getStatusPageUrl();
    }

    @Override
    public HashSet<String> getHealthCheckUrls() {
        if (healthCheckUrls != null || grpcInstanceInfo.getHealthCheckUrlsList() == null) {
            return healthCheckUrls;
        }
        HashSet<String> healthCheckUrls = new HashSet<>(grpcInstanceInfo.getHealthCheckUrlsList().size());
        for (String healthCheckUrl : grpcInstanceInfo.getHealthCheckUrlsList()) {
            healthCheckUrls.add(healthCheckUrl);
        }
        this.healthCheckUrls = healthCheckUrls;
        return healthCheckUrls;
    }

    @Override
    public DataCenterInfo getDataCenterInfo() {
        Eureka2.GrpcDataCenterInfo grpcDataCenterInfo = grpcInstanceInfo.getDataCenterInfo();
        if (dataCenterInfo != null || grpcDataCenterInfo == null) {
            return dataCenterInfo;
        }
        DataCenterInfo dataCenterInfo;
        switch (grpcDataCenterInfo.getOneofDataCenterInfoCase()) {
            case BASIC:
                dataCenterInfo = GrpcBasicDataCenterInfoWrapper.asBasicDataCenterInfo(grpcDataCenterInfo);
                break;
            case AWS:
                dataCenterInfo = GrpcAwsDataCenterInfoWrapper.asAwsDataCenterInfo(grpcDataCenterInfo);
                break;
            case ONEOFDATACENTERINFO_NOT_SET:
                dataCenterInfo = null;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized data center type " + grpcDataCenterInfo.getOneofDataCenterInfoCase());
        }
        this.dataCenterInfo = dataCenterInfo;
        return dataCenterInfo;
    }

    @Override
    public Map<String, String> getMetaData() {
        return grpcInstanceInfo.getMetadata();
    }

    @Override
    public Iterator<ServiceEndpoint> serviceEndpoints() {
        return ServiceEndpointImpl.iteratorFrom(this);
    }

    @Override
    public Eureka2.GrpcInstanceInfo getGrpcObject() {
        return grpcInstanceInfo;
    }

    @Override
    public int hashCode() {
        return grpcInstanceInfo.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GrpcInstanceInfoWrapper) {
            return grpcInstanceInfo.equals(((GrpcInstanceInfoWrapper) obj).getGrpcObject());
        }
        return false;
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcInstanceInfo);
    }

    public static InstanceInfo asInstanceInfo(Eureka2.GrpcInstanceInfo grpcInstanceInfo) {
        return new GrpcInstanceInfoWrapper(grpcInstanceInfo);
    }

    public static Eureka2.GrpcInstanceInfo asGrpcInstanceInfo(InstanceInfo instanceInfo) {
        return ((GrpcInstanceInfoWrapper) instanceInfo).getGrpcObject();
    }

    public static Status toStatus(Eureka2.GrpcInstanceInfo.GrpcStatus grpcStatus) {
        if (grpcStatus == null) {
            return null;
        }
        switch (grpcStatus) {
            case DOWN:
                return Status.DOWN;
            case OUT_OF_SERVICE:
                return Status.OUT_OF_SERVICE;
            case STARTING:
                return Status.STARTING;
            case UP:
                return Status.UP;
        }
        return Status.UNKNOWN;
    }

    public static Eureka2.GrpcInstanceInfo.GrpcStatus toGrpcStatus(Status status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case DOWN:
                return Eureka2.GrpcInstanceInfo.GrpcStatus.DOWN;
            case OUT_OF_SERVICE:
                return Eureka2.GrpcInstanceInfo.GrpcStatus.OUT_OF_SERVICE;
            case STARTING:
                return Eureka2.GrpcInstanceInfo.GrpcStatus.STARTING;
            case UP:
                return Eureka2.GrpcInstanceInfo.GrpcStatus.UP;
        }
        return Eureka2.GrpcInstanceInfo.GrpcStatus.UNRECOGNIZED;
    }

    public static class GrpcInstanceInfoWrapperBuilder extends InstanceInfoBuilder {
        @Override
        public InstanceInfo build() {
            Eureka2.GrpcInstanceInfo.Builder builder = Eureka2.GrpcInstanceInfo.newBuilder();

            if (app != null) {
                builder.setApp(app);
            }
            if (appGroup != null) {
                builder.setAppGroup(appGroup);
            }
            if (asg != null) {
                builder.setAsg(asg);
            }
            if (healthCheckUrls != null) {
                builder.addAllHealthCheckUrls(healthCheckUrls);
            }
            if (homePageUrl != null) {
                builder.setHomePageUrl(homePageUrl);
            }
            if (id != null) {
                builder.setId(id);
            }
            if (vipAddress != null) {
                builder.setVipAddress(vipAddress);
            }
            if (secureVipAddress != null) {
                builder.setSecureVipAddress(secureVipAddress);
            }
            if (status != null) {
                builder.setStatus(toGrpcStatus(status));
            }
            if (statusPageUrl != null) {
                builder.setStatusPageUrl(statusPageUrl);
            }

            if (ports != null && !ports.isEmpty()) {
                for (ServicePort port : ports) {
                    builder.addPorts(((GrpcServicePortWrapper) port).getGrpcObject());
                }
            }
            if (dataCenterInfo != null) {
                builder.setDataCenterInfo(((GrpcObjectWrapper<Eureka2.GrpcDataCenterInfo>) dataCenterInfo).getGrpcObject());
            }
            if(metaData != null) {
                builder.putAllMetadata(metaData);
            }

            return asInstanceInfo(builder.build());
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

        public static Iterator<ServiceEndpoint> iteratorFrom(final InstanceInfo instanceInfo) {
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
