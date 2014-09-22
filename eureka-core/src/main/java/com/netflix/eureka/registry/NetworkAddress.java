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


/**
 * @author Tomasz Bak
 */
public class NetworkAddress {

    enum ProtocolType {
        IPv4, IPv6
    }

    private final ProtocolType protocolType;
    private final boolean publicAddress;
    private final String ipAddress;
    private final String hostName;

    // For dynamic creation.
    protected NetworkAddress() {
        protocolType = null;
        publicAddress = false;
        ipAddress = hostName = null;
    }

    public NetworkAddress(ProtocolType protocolType, boolean publicAddress, String ipAddress, String hostName) {
        this.protocolType = protocolType;
        this.publicAddress = publicAddress;
        this.ipAddress = ipAddress;
        this.hostName = hostName;
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

    public boolean isPublic() {
        return publicAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NetworkAddress that = (NetworkAddress) o;

        if (publicAddress != that.publicAddress) {
            return false;
        }
        if (hostName != null ? !hostName.equals(that.hostName) : that.hostName != null) {
            return false;
        }
        if (ipAddress != null ? !ipAddress.equals(that.ipAddress) : that.ipAddress != null) {
            return false;
        }
        if (protocolType != null ? !protocolType.equals(that.protocolType) : that.protocolType != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = protocolType != null ? protocolType.hashCode() : 0;
        result = 31 * result + (publicAddress ? 1 : 0);
        result = 31 * result + (ipAddress != null ? ipAddress.hashCode() : 0);
        result = 31 * result + (hostName != null ? hostName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NetworkAddress{" +
                "protocolType=" + protocolType +
                ", publicAddress=" + publicAddress +
                ", ipAddress='" + ipAddress + '\'' +
                ", hostName='" + hostName + '\'' +
                '}';
    }

    public static NetworkAddress publicIPv4(String ipAddress) {
        return new NetworkAddress(ProtocolType.IPv4, true, ipAddress, null);
    }

    public static NetworkAddress publicHostNameIPv4(String hostName) {
        return new NetworkAddress(ProtocolType.IPv4, true, null, hostName);
    }

    public static NetworkAddress privateIPv4(String ipAddress) {
        return new NetworkAddress(ProtocolType.IPv4, false, ipAddress, null);
    }

    public static NetworkAddress privateHostNameIPv4(String hostName) {
        return new NetworkAddress(ProtocolType.IPv4, false, null, hostName);
    }
}
