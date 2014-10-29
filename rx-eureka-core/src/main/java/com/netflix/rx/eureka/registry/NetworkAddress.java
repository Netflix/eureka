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

package com.netflix.rx.eureka.registry;


/**
 * @author Tomasz Bak
 */
public class NetworkAddress {

    public static final String PUBLIC_ADDRESS = "public";
    public static final String PRIVATE_ADDRESS = "private";

    public enum ProtocolType {
        IPv4, IPv6
    }

    private final ProtocolType protocolType;

    private final String label;
    private final String ipAddress;
    private final String hostName;

    // For dynamic creation.
    protected NetworkAddress() {
        protocolType = null;
        label = ipAddress = hostName = null;
    }

    public NetworkAddress(String label, ProtocolType protocolType, String ipAddress, String hostName) {
        this.label = label;
        this.protocolType = protocolType;
        this.ipAddress = ipAddress;
        this.hostName = hostName;
    }

    public String getLabel() {
        return label;
    }

    public boolean hasLabel(String otherLabel) {
        return label.equals(otherLabel);
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NetworkAddress address = (NetworkAddress) o;

        if (hostName != null ? !hostName.equals(address.hostName) : address.hostName != null) {
            return false;
        }
        if (ipAddress != null ? !ipAddress.equals(address.ipAddress) : address.ipAddress != null) {
            return false;
        }
        if (label != null ? !label.equals(address.label) : address.label != null) {
            return false;
        }
        if (protocolType != address.protocolType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = protocolType != null ? protocolType.hashCode() : 0;
        result = 31 * result + (label != null ? label.hashCode() : 0);
        result = 31 * result + (ipAddress != null ? ipAddress.hashCode() : 0);
        result = 31 * result + (hostName != null ? hostName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NetworkAddress{" +
                "protocolType=" + protocolType +
                ", label='" + label + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", hostName='" + hostName + '\'' +
                '}';
    }

    public static class NetworkAddressBuilder {
        private ProtocolType protocolType;
        private String label;
        private String ipAddress;
        private String hostName;

        private NetworkAddressBuilder() {
        }

        public static NetworkAddressBuilder aNetworkAddress() {
            return new NetworkAddressBuilder();
        }

        public NetworkAddressBuilder withProtocolType(ProtocolType protocolType) {
            this.protocolType = protocolType;
            return this;
        }

        public NetworkAddressBuilder withLabel(String label) {
            this.label = label;
            return this;
        }

        public NetworkAddressBuilder withIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public NetworkAddressBuilder withHostName(String hostName) {
            this.hostName = hostName;
            return this;
        }

        public NetworkAddressBuilder but() {
            return aNetworkAddress().withProtocolType(protocolType).withLabel(label).withIpAddress(ipAddress).withHostName(hostName);
        }

        public NetworkAddress build() {
            return new NetworkAddress(label, protocolType, ipAddress, hostName);
        }
    }
}
