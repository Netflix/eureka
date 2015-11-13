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

package com.netflix.eureka2.model.datacenter;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.netflix.eureka2.model.instance.NetworkAddress;

import static com.netflix.eureka2.model.instance.StdNetworkAddress.NetworkAddressBuilderImpl.aNetworkAddress;
import static com.netflix.eureka2.model.instance.StdNetworkAddress.PRIVATE_ADDRESS;
import static com.netflix.eureka2.model.instance.StdNetworkAddress.PUBLIC_ADDRESS;
import static com.netflix.eureka2.model.instance.NetworkAddress.ProtocolType;

/**
 * This class represents a location of a server in AWS datacenter.
 *
 * @author Tomasz Bak
 */
public class StdAwsDataCenterInfo implements AwsDataCenterInfo {

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

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    private final NetworkAddress publicAddress;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    private final NetworkAddress privateAddress;

    // For object creation via reflection.
    private StdAwsDataCenterInfo() {
        name = region = zone = placementGroup = amiId = instanceId = instanceType = eth0mac = vpcId = accountId = null;
        publicAddress = privateAddress = null;
    }

    private StdAwsDataCenterInfo(Builder builder) {
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

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRegion() {
        return region;
    }

    @Override
    public String getZone() {
        return zone;
    }

    @Override
    public String getPlacementGroup() {
        return placementGroup;
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

    @Override
    public NetworkAddress getPublicAddress() {
        return publicAddress;
    }

    @Override
    public NetworkAddress getPrivateAddress() {
        return privateAddress;
    }

    @Override
    public String getAmiId() {
        return amiId;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getInstanceType() {
        return instanceType;
    }

    @Override
    public String getVpcId() {
        return vpcId;
    }

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public String getEth0mac() {
        return eth0mac;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof StdAwsDataCenterInfo))
            return false;

        StdAwsDataCenterInfo that = (StdAwsDataCenterInfo) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null)
            return false;
        if (amiId != null ? !amiId.equals(that.amiId) : that.amiId != null)
            return false;
        if (eth0mac != null ? !eth0mac.equals(that.eth0mac) : that.eth0mac != null)
            return false;
        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null)
            return false;
        if (instanceType != null ? !instanceType.equals(that.instanceType) : that.instanceType != null)
            return false;
        if (name != null ? !name.equals(that.name) : that.name != null)
            return false;
        if (placementGroup != null ? !placementGroup.equals(that.placementGroup) : that.placementGroup != null)
            return false;
        if (privateAddress != null ? !privateAddress.equals(that.privateAddress) : that.privateAddress != null)
            return false;
        if (publicAddress != null ? !publicAddress.equals(that.publicAddress) : that.publicAddress != null)
            return false;
        if (region != null ? !region.equals(that.region) : that.region != null)
            return false;
        if (vpcId != null ? !vpcId.equals(that.vpcId) : that.vpcId != null)
            return false;
        if (zone != null ? !zone.equals(that.zone) : that.zone != null)
            return false;

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

    public static final class Builder extends AwsDataCenterInfoBuilder {
        @Override
        public StdAwsDataCenterInfo build() {
            if (region == null && zone != null && !zone.isEmpty()) { // We will take it from zone name
                region = zone.substring(0, zone.length() - 1);
            }
            return new StdAwsDataCenterInfo(this);
        }
    }
}
