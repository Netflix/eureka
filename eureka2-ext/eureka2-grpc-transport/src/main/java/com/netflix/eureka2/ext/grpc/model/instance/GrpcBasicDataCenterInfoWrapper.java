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
import com.netflix.eureka2.model.datacenter.BasicDataCenterInfo;
import com.netflix.eureka2.model.datacenter.BasicDataCenterInfoBuilder;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.NetworkAddress;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class GrpcBasicDataCenterInfoWrapper implements GrpcObjectWrapper<Eureka2.GrpcDataCenterInfo>, BasicDataCenterInfo {
    private final Eureka2.GrpcDataCenterInfo grpcDataCenterInfo;
    private volatile List<NetworkAddress> networkAddresses;

    public GrpcBasicDataCenterInfoWrapper(Eureka2.GrpcDataCenterInfo grpcDataCenterInfo) {
        this.grpcDataCenterInfo = grpcDataCenterInfo;
    }

    @Override
    public String getName() {
        return grpcDataCenterInfo.getBasic().getName();
    }

    @Override
    public List<NetworkAddress> getAddresses() {
        List<Eureka2.GrpcNetworkAddress> grpcNetworkAddresses = grpcDataCenterInfo.getBasic().getAddressesList();
        if (networkAddresses != null || grpcNetworkAddresses == null) {
            return networkAddresses;
        }
        List<NetworkAddress> networkAddresses = new ArrayList<>(grpcNetworkAddresses.size());
        for (Eureka2.GrpcNetworkAddress grpcNetworkAddress : grpcNetworkAddresses) {
            networkAddresses.add(GrpcNetworkAddressWrapper.asNetworkAddress(grpcNetworkAddress));
        }
        this.networkAddresses = networkAddresses;
        return networkAddresses;
    }

    @Override
    public NetworkAddress getDefaultAddress() {
        List<NetworkAddress> networkAddresses = getAddresses();
        if (networkAddresses == null || networkAddresses.isEmpty()) {
            return null;
        }
        return networkAddresses.get(0);
    }

    public static GrpcBasicDataCenterInfoWrapper asBasicDataCenterInfo(Eureka2.GrpcDataCenterInfo grpcDataCenterInfo) {
        return new GrpcBasicDataCenterInfoWrapper(grpcDataCenterInfo);
    }

    @Override
    public Eureka2.GrpcDataCenterInfo getGrpcObject() {
        return grpcDataCenterInfo;
    }

    public static class GrpcBasicDataCenterInfoWrapperBuilder extends BasicDataCenterInfoBuilder {

        @Override
        public BasicDataCenterInfo build() {
            Eureka2.GrpcDataCenterInfo.GrpcBasicDataCenterInfo.Builder builder = Eureka2.GrpcDataCenterInfo.GrpcBasicDataCenterInfo.newBuilder()
                    .setName(name);
            for (NetworkAddress networkAddress : addresses) {
                builder.addAddresses(GrpcNetworkAddressWrapper.asGrpcNetworkAddress(networkAddress));
            }
            Eureka2.GrpcDataCenterInfo grpcDataCenterInfo = Eureka2.GrpcDataCenterInfo.newBuilder()
                    .setBasic(builder.build())
                    .build();
            return new GrpcBasicDataCenterInfoWrapper(grpcDataCenterInfo);
        }
    }
}
