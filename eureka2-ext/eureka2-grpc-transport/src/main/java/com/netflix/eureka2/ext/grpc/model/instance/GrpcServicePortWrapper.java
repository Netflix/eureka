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
import com.netflix.eureka2.model.instance.ServicePort;

import java.util.HashSet;
import java.util.Set;

/**
 */
public class GrpcServicePortWrapper implements ServicePort, GrpcObjectWrapper<Eureka2.GrpcServicePort> {
    private final Eureka2.GrpcServicePort grpcServicePort;

    private volatile Set<String> addressLabels;

    public GrpcServicePortWrapper(Eureka2.GrpcServicePort grpcServicePort) {
        this.grpcServicePort = grpcServicePort;
    }

    @Override
    public String getName() {
        return grpcServicePort.getName();
    }

    @Override
    public Integer getPort() {
        return grpcServicePort.getPort();
    }

    @Override
    public boolean isSecure() {
        return grpcServicePort.getSecure();
    }

    @Override
    public Set<String> getAddressLabels() {
        if (addressLabels != null || grpcServicePort.getAddressLabelsList() == null) {
            return addressLabels;
        }
        addressLabels = new HashSet<>(grpcServicePort.getAddressLabelsList());
        return addressLabels;
    }

    @Override
    public Eureka2.GrpcServicePort getGrpcObject() {
        return grpcServicePort;
    }

    public static GrpcServicePortWrapper asServicePort(Eureka2.GrpcServicePort grpcServicePort) {
        return new GrpcServicePortWrapper(grpcServicePort);
    }

    public static ServicePort newServicePort(String name, int port, boolean secure, Set<String> labels) {
        Eureka2.GrpcServicePort.Builder builder = Eureka2.GrpcServicePort.newBuilder();
        if (name != null) {
            builder.setName(name);
        }
        builder.setPort(port);
        builder.setSecure(secure);
        if (labels != null) {
            builder.addAllAddressLabels(labels);
        }

        return asServicePort(builder.build());
    }
}
