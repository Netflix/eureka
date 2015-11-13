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

package com.netflix.eureka2.model;

import java.util.Set;

import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.model.datacenter.AwsDataCenterInfoBuilder;
import com.netflix.eureka2.model.datacenter.StdAwsDataCenterInfo;
import com.netflix.eureka2.model.datacenter.BasicDataCenterInfoBuilder;
import com.netflix.eureka2.model.datacenter.StdBasicDataCenterInfo.Builder;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.StdDelta;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.instance.NetworkAddressBuilder;
import com.netflix.eureka2.model.instance.StdNetworkAddress.NetworkAddressBuilderImpl;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.model.instance.StdServicePort;

/**
 */
public class StdInstanceModel extends InstanceModel {

    private static final InstanceModel INSTANCE = new StdInstanceModel();

    public static InstanceModel getStdModel() {
        return INSTANCE;
    }

    @Override
    public Source createSource(Origin origin) {
        return new StdSource(origin);
    }

    @Override
    public Source createSource(Origin origin, String name) {
        return new StdSource(origin, name);
    }

    @Override
    public Source createSource(Origin origin, String name, long id) {
        return new StdSource(origin, name, id);
    }

    @Override
    public BasicDataCenterInfoBuilder newBasicDataCenterInfo() {
        return new Builder<>();
    }

    @Override
    public AwsDataCenterInfoBuilder newAwsDataCenterInfo() {
        return new StdAwsDataCenterInfo.Builder();
    }

    @Override
    public NetworkAddressBuilder newNetworkAddress() {
        return new NetworkAddressBuilderImpl();
    }

    @Override
    public DeltaBuilder newDelta() {
        return new StdDelta.Builder();
    }

    @Override
    public InstanceInfoBuilder newInstanceInfo() {
        return new StdInstanceInfo.Builder();
    }

    @Override
    public ServicePort newServicePort(int port, boolean secure) {
        return new StdServicePort(port, secure);
    }

    @Override
    public ServicePort newServicePort(String name, int port, boolean secure) {
        return new StdServicePort(name, port, secure);
    }

    @Override
    public ServicePort newServicePort(String name, int port, boolean secure, Set<String> labels) {
        return new StdServicePort(name, port, secure, labels);
    }
}
