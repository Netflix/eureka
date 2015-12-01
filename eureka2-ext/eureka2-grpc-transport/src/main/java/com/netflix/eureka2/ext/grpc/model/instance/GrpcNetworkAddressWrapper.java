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

import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.NetworkAddress;
import com.netflix.eureka2.model.instance.NetworkAddressBuilder;

/**
 */
public class GrpcNetworkAddressWrapper implements NetworkAddress {
    private final Eureka2.GrpcNetworkAddress grpcNetworkAddress;

    public GrpcNetworkAddressWrapper(Eureka2.GrpcNetworkAddress grpcNetworkAddress) {
        this.grpcNetworkAddress = grpcNetworkAddress;
    }

    @Override
    public String getLabel() {
        return grpcNetworkAddress.getLabel();
    }

    @Override
    public boolean hasLabel(String otherLabel) {
        return false;
    }

    @Override
    public ProtocolType getProtocolType() {
        return toProtocolType(grpcNetworkAddress.getProtocolType());
    }

    @Override
    public String getIpAddress() {
        return grpcNetworkAddress.getIpAddress();
    }

    @Override
    public String getHostName() {
        return grpcNetworkAddress.getHostName();
    }

    public static GrpcNetworkAddressWrapper asNetworkAddress(Eureka2.GrpcNetworkAddress grpcNetworkAddress) {
        return new GrpcNetworkAddressWrapper(grpcNetworkAddress);
    }

    public static Eureka2.GrpcNetworkAddress asGrpcNetworkAddress(NetworkAddress networkAddress) {
        return Eureka2.GrpcNetworkAddress.newBuilder()
                .setHostName(networkAddress.getHostName())
                .setIpAddress(networkAddress.getIpAddress())
                .setLabel(networkAddress.getLabel())
                .setProtocolType(toGrpcProtocolType(networkAddress.getProtocolType()))
                .build();
    }

    private static ProtocolType toProtocolType(Eureka2.GrpcNetworkAddress.GrpcProtocolType grpcProtocolType) {
        if (grpcProtocolType == null) {
            return null;
        }
        switch (grpcProtocolType) {
            case IPv4:
                return ProtocolType.IPv4;
            case IPv6:
                return ProtocolType.IPv6;
        }
        throw new IllegalArgumentException("Unrecognized ProtocolType " + grpcProtocolType);
    }

    private static Eureka2.GrpcNetworkAddress.GrpcProtocolType toGrpcProtocolType(ProtocolType protocolType) {
        if (protocolType == null) {
            return null;
        }
        switch (protocolType) {
            case IPv4:
                return Eureka2.GrpcNetworkAddress.GrpcProtocolType.IPv4;
            case IPv6:
                return Eureka2.GrpcNetworkAddress.GrpcProtocolType.IPv6;
        }
        throw new IllegalArgumentException("Unrecognized ProtocolType " + protocolType);
    }

    public static class GrpcNetworkAddressWrapperBuilder extends NetworkAddressBuilder {
        @Override
        public NetworkAddress build() {
            Eureka2.GrpcNetworkAddress.Builder builder = Eureka2.GrpcNetworkAddress.newBuilder();
            if (label != null) {
                builder.setLabel(label);
            }
            if (protocolType != null) {
                builder.setProtocolType(toGrpcProtocolType(protocolType));
            }
            if (hostName != null) {
                builder.setHostName(hostName);
            }
            if (ipAddress != null) {
                builder.setIpAddress(ipAddress);
            }
            return asNetworkAddress(builder.build());
        }
    }
}
