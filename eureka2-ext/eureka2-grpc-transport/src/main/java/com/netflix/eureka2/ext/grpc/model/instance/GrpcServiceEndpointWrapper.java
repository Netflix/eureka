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
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.NetworkAddress;
import com.netflix.eureka2.model.instance.ServiceEndpoint;
import com.netflix.eureka2.model.instance.ServicePort;

/**
 */
public class GrpcServiceEndpointWrapper implements ServiceEndpoint, GrpcObjectWrapper<Eureka2.GrpcServiceEndpoint> {

    private final Eureka2.GrpcServiceEndpoint grpcServiceEndpoint;

    private volatile ServicePort servicePort;
    private volatile NetworkAddress networkAddress;

    public GrpcServiceEndpointWrapper(Eureka2.GrpcServiceEndpoint grpcServiceEndpoint) {
        this.grpcServiceEndpoint = grpcServiceEndpoint;
    }

    @Override
    public NetworkAddress getAddress() {
        if (networkAddress == null) {
            networkAddress = new GrpcNetworkAddressWrapper(grpcServiceEndpoint.getAddress());
        }
        return networkAddress;
    }

    @Override
    public ServicePort getServicePort() {
        if (servicePort == null) {
            servicePort = new GrpcServicePortWrapper(grpcServiceEndpoint.getServicePort());
        }
        return servicePort;
    }

    @Override
    public Eureka2.GrpcServiceEndpoint getGrpcObject() {
        return grpcServiceEndpoint;
    }

    @Override
    public int hashCode() {
        return grpcServiceEndpoint.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GrpcServiceEndpointWrapper) {
            return grpcServiceEndpoint.equals(((GrpcServiceEndpointWrapper) obj).getGrpcObject());
        }
        return false;
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcServiceEndpoint);
    }

    public static ServiceEndpoint asServiceEndpoint(Eureka2.GrpcServiceEndpoint grpcServiceEndpoint) {
        return new GrpcServiceEndpointWrapper(grpcServiceEndpoint);
    }
}
