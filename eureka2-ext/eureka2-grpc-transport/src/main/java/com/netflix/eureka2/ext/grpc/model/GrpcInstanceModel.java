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

package com.netflix.eureka2.ext.grpc.model;

import com.netflix.eureka2.ext.grpc.model.instance.*;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.datacenter.AwsDataCenterInfoBuilder;
import com.netflix.eureka2.model.datacenter.BasicDataCenterInfoBuilder;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.instance.NetworkAddressBuilder;
import com.netflix.eureka2.model.instance.ServicePort;

import java.util.Set;

/**
 */
public class GrpcInstanceModel extends InstanceModel {

    private static final InstanceModel INSTANCE = new GrpcInstanceModel();

    public static InstanceModel getGrpcModel() {
        return INSTANCE;
    }

    @Override
    public Source createSource(Source.Origin origin) {
        return GrpcSourceWrapper.newSource(origin, null, -1);
    }

    @Override
    public Source createSource(Source.Origin origin, String name) {
        return GrpcSourceWrapper.newSource(origin, name, -1);
    }

    @Override
    public Source createSource(Source.Origin origin, String name, long id) {
        return GrpcSourceWrapper.newSource(origin, name, (int) id);
    }

    @Override
    public BasicDataCenterInfoBuilder newBasicDataCenterInfo() {
        return new GrpcBasicDataCenterInfoWrapper.GrpcBasicDataCenterInfoWrapperBuilder();
    }

    @Override
    public AwsDataCenterInfoBuilder newAwsDataCenterInfo() {
        return new GrpcAwsDataCenterInfoWrapper.GrpcAwsDataCenterInfoWrapperBuilder();
    }

    @Override
    public NetworkAddressBuilder newNetworkAddress() {
        return new GrpcNetworkAddressWrapper.GrpcNetworkAddressWrapperBuilder();
    }

    @Override
    public DeltaBuilder newDelta() {
        return new GrpcDeltaWrapper.Builder();
    }

    @Override
    public InstanceInfoBuilder newInstanceInfo() {
        return new GrpcInstanceInfoWrapper.GrpcInstanceInfoWrapperBuilder();
    }

    @Override
    public ServicePort newServicePort(int port, boolean secure) {
        return GrpcServicePortWrapper.newServicePort(null, port, secure, null);
    }

    @Override
    public ServicePort newServicePort(String name, int port, boolean secure) {
        return GrpcServicePortWrapper.newServicePort(name, port, secure, null);
    }

    @Override
    public ServicePort newServicePort(String name, int port, boolean secure, Set<String> labels) {
        return GrpcServicePortWrapper.newServicePort(name, port, secure, labels);
    }
}
