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

package com.netflix.eureka2.model.selector;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.netflix.eureka2.internal.util.SystemUtil;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.NetworkAddress;
import com.netflix.eureka2.model.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.model.instance.ServiceEndpoint;

/**
 * A query DSL for {@link InstanceInfo} object. For deployments where an application
 * runs on a server with multiple NICs, runs services on multiple ports and has
 * different network connection types (private, public, management, etc), extracting information
 * about a particular service endpoint (host/ip + port) directly is cumbersome. This class simplifies
 * this process by allowing a user expressing the criteria in a declarative way, and get
 * a list of all service endpoints that meet them.
 *
 * <h1>Example</h1>
 * I need to connect to 'webAdmin' application over a secure communication channel, that runs on a server
 * described by {@link InstanceInfo} object 'myInstanceInfo'. This query returns
 * a desired address (IP/port):
 * <p>
 * {@code
 * ServiceSelector.selectBy().serviceLabel("webAdmin").secure(true).returnServiceAddress(myInstanceInfo);
 * }
 *
 * @author Tomasz Bak
 */
public final class ServiceSelector extends DataSelector<ServiceEndpoint, ServiceSelector> {

    private ServiceSelector() {
    }

    public static ServiceSelector selectBy() {
        return new ServiceSelector();
    }

    public ServiceSelector protocolType(ProtocolType... protocolTypes) {
        current.add(new ProtocolTypeCriteria(protocolTypes));
        return this;
    }

    public ServiceSelector addressLabel(String... addressLabels) {
        current.add(new AddressLabelCriteria(addressLabels));
        return this;
    }

    public ServiceSelector serviceLabel(String... serviceLabels) {
        current.add(new ServiceLabelCriteria(serviceLabels));
        return this;
    }

    public ServiceSelector secure(boolean securePort) {
        current.add(new SecurePortCriteria(securePort));
        return this;
    }

    public ServiceSelector publicIp(boolean publicAddress) {
        current.add(new PublicIpCriteria(publicAddress));
        return this;
    }

    public InetSocketAddress returnServiceAddress(InstanceInfo instanceInfo) {
        ServiceEndpoint endpoint = returnServiceEndpoint(instanceInfo);
        if (endpoint == null) {
            return null;
        }
        String address = endpoint.getAddress().getHostName();
        if (address == null) {
            address = endpoint.getAddress().getIpAddress();
        }
        return new InetSocketAddress(address, endpoint.getServicePort().getPort());
    }

    public NetworkAddress returnNetworkAddress(InstanceInfo instanceInfo) {
        ServiceEndpoint endpoint = returnServiceEndpoint(instanceInfo);
        return endpoint != null ? endpoint.getAddress() : null;
    }

    public List<NetworkAddress> returnNetworkAddresses(InstanceInfo instanceInfo) {
        List<NetworkAddress> addresses = new ArrayList<>();
        Iterator<ServiceEndpoint> it = returnServiceEndpoints(instanceInfo);
        while (it.hasNext()) {
            addresses.add(it.next().getAddress());
        }
        return addresses;
    }

    public ServiceEndpoint returnServiceEndpoint(InstanceInfo instanceInfo) {
        Iterator<ServiceEndpoint> it = returnServiceEndpoints(instanceInfo);
        return it.hasNext() ? it.next() : null;
    }

    public Iterator<ServiceEndpoint> returnServiceEndpoints(InstanceInfo instanceInfo) {
        final Iterator<ServiceEndpoint> endpointIt = instanceInfo.serviceEndpoints();
        return applyQuery(endpointIt);
    }

    static class ProtocolTypeCriteria extends Criteria<ServiceEndpoint, ProtocolType> {
        ProtocolTypeCriteria(ProtocolType... protocolTypes) {
            super(protocolTypes);
        }

        @Override
        protected boolean matches(ProtocolType value, ServiceEndpoint endpoint) {
            return endpoint.getAddress().getProtocolType() == value;
        }
    }

    static class AddressLabelCriteria extends Criteria<ServiceEndpoint, String> {
        AddressLabelCriteria(String... labels) {
            super(labels);
        }

        @Override
        protected boolean matches(String value, ServiceEndpoint endpoint) {
            return value.equals(endpoint.getAddress().getLabel());
        }
    }

    static class ServiceLabelCriteria extends Criteria<ServiceEndpoint, String> {
        ServiceLabelCriteria(String... labels) {
            super(labels);
        }

        @Override
        protected boolean matches(String value, ServiceEndpoint endpoint) {
            return value.equals(endpoint.getServicePort().getName());
        }
    }

    static class SecurePortCriteria extends Criteria<ServiceEndpoint, Boolean> {
        SecurePortCriteria(boolean securePort) {
            super(securePort);
        }

        @Override
        protected boolean matches(Boolean value, ServiceEndpoint endpoint) {
            return endpoint.getServicePort().isSecure() == value;
        }
    }

    static class PublicIpCriteria extends Criteria<ServiceEndpoint, Boolean> {
        PublicIpCriteria(boolean publicAddress) {
            super(publicAddress);
        }

        @Override
        protected boolean matches(Boolean value, ServiceEndpoint endpoint) {
            String ipAddress = endpoint.getAddress().getIpAddress();
            if (ipAddress == null) {
                return false;
            }
            return SystemUtil.isPublic(ipAddress) == value;
        }
    }
}
