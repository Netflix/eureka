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

package com.netflix.eureka2.registry.datacenter;

import com.netflix.eureka2.registry.instance.NetworkAddress;

import java.util.ArrayList;
import java.util.List;

import static com.netflix.eureka2.registry.instance.NetworkAddress.NetworkAddressBuilder.aNetworkAddress;
import static com.netflix.eureka2.registry.instance.NetworkAddress.PRIVATE_ADDRESS;
import static com.netflix.eureka2.registry.instance.NetworkAddress.PUBLIC_ADDRESS;
import static com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;

/**
 * This class represents a location of a server in AWS datacenter.
 *
 * @author Tomasz Bak
 */
public class AwsDataCenterInfo extends DataCenterInfo {

    private final String name;
    private final String region;
    private final String zone;
    private final String placementGroup;
    private final String amiId;
    private final String instanceId;
    private final String instanceType;
    private final String eth0mac;
    private final String vpcId;
    private final String accountId;
    private final NetworkAddress publicAddress;
    private final NetworkAddress privateAddress;

    // For object creation via reflection.
    private AwsDataCenterInfo() {
        name = region = zone = placementGroup = amiId = instanceId = instanceType = eth0mac = vpcId = accountId = null;
        publicAddress = privateAddress = null;
    }

    private AwsDataCenterInfo(Builder builder) {
        region = builder.region;
        zone = builder.zone;
        placementGroup = builder.placementGroup;
        amiId = builder.amiId;
        name = instanceId = builder.instanceId;
        instanceType = builder.instanceType;
        eth0mac = builder.eth0mac;
        vpcId = builder.vpcId;
        accountId = builder.accountId;

        if (builder.privateIP != null || builder.privateHostName != null) {
            privateAddress = aNetworkAddress().withLabel(PRIVATE_ADDRESS).withProtocolType(ProtocolType.IPv4)
                    .withHostName(builder.privateHostName).withIpAddress(builder.privateIP).build();
        } else {
            privateAddress = null;
        }
        if (builder.publicIP != null || builder.publicHostName != null) {
            publicAddress = aNetworkAddress().withLabel(PUBLIC_ADDRESS).withProtocolType(ProtocolType.IPv4)
                    .withHostName(builder.publicHostName).withIpAddress(builder.publicIP).build();
        } else {
            publicAddress = null;
        }
    }

    public String getRegion() {
        return region;
    }

    public String getZone() {
        return zone;
    }

    public String getPlacementGroup() {
        return placementGroup;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<NetworkAddress> getAddresses() {
        List<NetworkAddress> addresses = new ArrayList<>(2);
        if (publicAddress != null) {
            addresses.add(publicAddress);
        }
        if (privateAddress != null) {
            addresses.add(privateAddress);
        }
        return addresses;
    }

    /**
     * The order of selection: first public, next private.
     */
    @Override
    public NetworkAddress getDefaultAddress() {
        return publicAddress != null ? publicAddress : privateAddress;
    }

    public NetworkAddress getPublicAddress() {
        return publicAddress;
    }

    public NetworkAddress getPrivateAddress() {
        return privateAddress;
    }

    public String getAmiId() {
        return amiId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public String getVpcId() {
        return vpcId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getEth0mac() {
        return eth0mac;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AwsDataCenterInfo)) return false;

        AwsDataCenterInfo that = (AwsDataCenterInfo) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) return false;
        if (amiId != null ? !amiId.equals(that.amiId) : that.amiId != null) return false;
        if (eth0mac != null ? !eth0mac.equals(that.eth0mac) : that.eth0mac != null) return false;
        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) return false;
        if (instanceType != null ? !instanceType.equals(that.instanceType) : that.instanceType != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (placementGroup != null ? !placementGroup.equals(that.placementGroup) : that.placementGroup != null)
            return false;
        if (privateAddress != null ? !privateAddress.equals(that.privateAddress) : that.privateAddress != null)
            return false;
        if (publicAddress != null ? !publicAddress.equals(that.publicAddress) : that.publicAddress != null)
            return false;
        if (region != null ? !region.equals(that.region) : that.region != null) return false;
        if (vpcId != null ? !vpcId.equals(that.vpcId) : that.vpcId != null) return false;
        if (zone != null ? !zone.equals(that.zone) : that.zone != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (region != null ? region.hashCode() : 0);
        result = 31 * result + (zone != null ? zone.hashCode() : 0);
        result = 31 * result + (placementGroup != null ? placementGroup.hashCode() : 0);
        result = 31 * result + (amiId != null ? amiId.hashCode() : 0);
        result = 31 * result + (instanceId != null ? instanceId.hashCode() : 0);
        result = 31 * result + (instanceType != null ? instanceType.hashCode() : 0);
        result = 31 * result + (eth0mac != null ? eth0mac.hashCode() : 0);
        result = 31 * result + (vpcId != null ? vpcId.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (publicAddress != null ? publicAddress.hashCode() : 0);
        result = 31 * result + (privateAddress != null ? privateAddress.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AwsDataCenterInfo{" +
                "name='" + name + '\'' +
                ", region='" + region + '\'' +
                ", zone='" + zone + '\'' +
                ", placementGroup='" + placementGroup + '\'' +
                ", amiId='" + amiId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", instanceType='" + instanceType + '\'' +
                ", eth0mac='" + eth0mac + '\'' +
                ", vpcId='" + vpcId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", publicAddress=" + publicAddress +
                ", privateAddress=" + privateAddress +
                '}';
    }

    public static final class Builder extends DataCenterInfoBuilder<AwsDataCenterInfo> {
        private String region;
        private String zone;
        private String placementGroup;
        private String amiId;
        private String instanceId;
        private String instanceType;
        private String privateIP;
        private String privateHostName;
        private String publicIP;
        private String publicHostName;
        private String eth0mac;
        private String vpcId;
        private String accountId;

        public Builder withAwsDataCenter(AwsDataCenterInfo dataCenter) {
            this.region = dataCenter.getRegion();
            this.zone = dataCenter.getZone();
            this.placementGroup = dataCenter.getPlacementGroup();
            this.amiId = dataCenter.getAmiId();
            this.instanceId = dataCenter.getInstanceId();
            this.instanceType = dataCenter.getInstanceType();
            this.privateIP = dataCenter.getPrivateAddress().getIpAddress();
            this.privateHostName = dataCenter.getPrivateAddress().getHostName();
            this.publicIP = dataCenter.getPublicAddress().getIpAddress();
            this.publicHostName = dataCenter.getPublicAddress().getHostName();
            this.eth0mac = dataCenter.getEth0mac();
            this.vpcId = dataCenter.getVpcId();
            this.accountId = dataCenter.getAccountId();

            return this;
        }

        public Builder withRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder withZone(String zone) {
            this.zone = zone;
            return this;
        }

        public Builder withPlacementGroup(String placementGroup) {
            this.placementGroup = placementGroup;
            return this;
        }

        public Builder withAmiId(String amiId) {
            this.amiId = amiId;
            return this;
        }

        public Builder withInstanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder withPrivateIPv4(String privateIP) {
            this.privateIP = privateIP;
            return this;
        }

        public Builder withPrivateHostName(String privateHostName) {
            this.privateHostName = privateHostName;
            return this;
        }

        public Builder withPublicIPv4(String publicIP) {
            this.publicIP = publicIP;
            return this;
        }

        public Builder withPublicHostName(String publicHostName) {
            this.publicHostName = publicHostName;
            return this;
        }

        public Builder withVpcId(String vpcId) {
            this.vpcId = vpcId;
            return this;
        }

        public Builder withAccountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder withEth0mac(String eth0mac) {
            this.eth0mac = eth0mac;
            return this;
        }

        @Override
        public AwsDataCenterInfo build() {
            if (region == null && zone != null && !zone.isEmpty()) { // We will take it from zone name
                region = zone.substring(0, zone.length() - 1);
            }

            return new AwsDataCenterInfo(this);
        }
    }
}
