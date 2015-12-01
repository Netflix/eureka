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

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.model.datacenter.AwsDataCenterInfoBuilder;
import com.netflix.eureka2.model.instance.NetworkAddress;

import java.util.ArrayList;
import java.util.List;

import static com.netflix.eureka2.model.instance.NetworkAddress.PRIVATE_ADDRESS;
import static com.netflix.eureka2.model.instance.NetworkAddress.PUBLIC_ADDRESS;
import static com.netflix.eureka2.model.instance.NetworkAddressBuilder.aNetworkAddress;

/**
 */
public class GrpcAwsDataCenterInfoWrapper implements GrpcObjectWrapper<Eureka2.GrpcDataCenterInfo>, AwsDataCenterInfo {
    private final Eureka2.GrpcDataCenterInfo grpcDataCenterInfo;

    private volatile NetworkAddress publicAddress;
    private volatile NetworkAddress privateAddress;
    private volatile List<NetworkAddress> networkAddresses;

    public GrpcAwsDataCenterInfoWrapper(Eureka2.GrpcDataCenterInfo grpcDataCenterInfo) {
        this.grpcDataCenterInfo = grpcDataCenterInfo;
    }

    @Override
    public String getRegion() {
        return grpcDataCenterInfo.getAws().getRegion();
    }

    @Override
    public String getZone() {
        return grpcDataCenterInfo.getAws().getZone();
    }

    @Override
    public String getPlacementGroup() {
        return grpcDataCenterInfo.getAws().getPlacementGroup();
    }

    @Override
    public NetworkAddress getPublicAddress() {
        Eureka2.GrpcNetworkAddress grpcNetworkAddress = grpcDataCenterInfo.getAws().getPublicAddress();
        if (this.publicAddress != null || grpcNetworkAddress == null) {
            return this.publicAddress;
        }
        publicAddress = GrpcNetworkAddressWrapper.asNetworkAddress(grpcNetworkAddress);
        return publicAddress;
    }

    @Override
    public NetworkAddress getPrivateAddress() {
        Eureka2.GrpcNetworkAddress grpcNetworkAddress = grpcDataCenterInfo.getAws().getPrivateAddress();
        if (this.privateAddress != null || grpcNetworkAddress == null) {
            return this.privateAddress;
        }
        privateAddress = GrpcNetworkAddressWrapper.asNetworkAddress(grpcNetworkAddress);
        return privateAddress;
    }

    @Override
    public String getAmiId() {
        return grpcDataCenterInfo.getAws().getAmiId();
    }

    @Override
    public String getInstanceId() {
        return grpcDataCenterInfo.getAws().getInstanceId();
    }

    @Override
    public String getInstanceType() {
        return grpcDataCenterInfo.getAws().getInstanceType();
    }

    @Override
    public String getVpcId() {
        return grpcDataCenterInfo.getAws().getVpcId();
    }

    @Override
    public String getAccountId() {
        return grpcDataCenterInfo.getAws().getAccountId();
    }

    @Override
    public String getEth0mac() {
        return grpcDataCenterInfo.getAws().getEth0Mac();
    }

    @Override
    public String getName() {
        return grpcDataCenterInfo.getAws().getName();
    }

    @Override
    public List<NetworkAddress> getAddresses() {
        if (networkAddresses != null) {
            return networkAddresses;
        }
        List<NetworkAddress> networkAddresses = new ArrayList<>(2);
        if (getPublicAddress() != null) {
            networkAddresses.add(getPublicAddress());
        }
        if (getPrivateAddress() != null) {
            networkAddresses.add(getPrivateAddress());
        }
        this.networkAddresses = networkAddresses;
        return networkAddresses;
    }

    @Override
    public NetworkAddress getDefaultAddress() {
        if (getPublicAddress() != null) {
            return getPublicAddress();
        }
        return getPrivateAddress();
    }

    public static GrpcAwsDataCenterInfoWrapper asAwsDataCenterInfo(Eureka2.GrpcDataCenterInfo grpcDataCenterInfo) {
        return new GrpcAwsDataCenterInfoWrapper(grpcDataCenterInfo);
    }

    @Override
    public Eureka2.GrpcDataCenterInfo getGrpcObject() {
        return grpcDataCenterInfo;
    }

    public static class GrpcAwsDataCenterInfoWrapperBuilder extends AwsDataCenterInfoBuilder {
        @Override
        public AwsDataCenterInfo build() {
            Eureka2.GrpcDataCenterInfo.GrpcAwsDataCenterInfo.Builder builder = Eureka2.GrpcDataCenterInfo.GrpcAwsDataCenterInfo.newBuilder();
            if (instanceId != null) {
                builder.setName(instanceId);
                builder.setInstanceId(instanceId);
            }
            if (accountId != null) {
                builder.setAccountId(accountId);
            }
            if (amiId != null) {
                builder.setAmiId(amiId);
            }
            if (eth0mac != null) {
                builder.setEth0Mac(eth0mac);
            }
            if (instanceType != null) {
                builder.setInstanceType(instanceType);
            }
            if (placementGroup != null) {
                builder.setPlacementGroup(placementGroup);
            }
            if (region != null) {
                builder.setRegion(region);
            }
            if (zone != null) {
                builder.setZone(zone);
            }
            if (vpcId != null) {
                builder.setVpcId(vpcId);
            }

            if (privateIP != null || privateHostName != null) {
                Eureka2.GrpcNetworkAddress.Builder privateIpBuilder = Eureka2.GrpcNetworkAddress.newBuilder();
                privateIpBuilder.setProtocolType(Eureka2.GrpcNetworkAddress.GrpcProtocolType.IPv4);
                privateIpBuilder.setLabel(PRIVATE_ADDRESS);
                privateIpBuilder.setIpAddress(privateIP);
                privateIpBuilder.setHostName(privateHostName);

                builder.setPrivateAddress(privateIpBuilder.build());
            }
            if (publicIP != null || publicHostName != null) {
                Eureka2.GrpcNetworkAddress.Builder publicIpBuilder = Eureka2.GrpcNetworkAddress.newBuilder();
                publicIpBuilder.setProtocolType(Eureka2.GrpcNetworkAddress.GrpcProtocolType.IPv4);
                publicIpBuilder.setLabel(PUBLIC_ADDRESS);
                publicIpBuilder.setIpAddress(publicIP);
                publicIpBuilder.setHostName(publicHostName);

                builder.setPublicAddress(publicIpBuilder.build());
            }

            return asAwsDataCenterInfo(
                    Eureka2.GrpcDataCenterInfo.newBuilder().setAws(builder.build()).build()
            );
        }
    }
}
