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
import com.netflix.eureka2.model.datacenter.BasicDataCenterInfoBuilder;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.instance.NetworkAddressBuilder;
import com.netflix.eureka2.model.instance.ServicePort;

/**
 */
public abstract class InstanceModel {

    private static volatile InstanceModel defaultModel;

    public abstract Source createSource(Origin origin);

    public abstract Source createSource(Origin origin, String name);

    public abstract Source createSource(Origin origin, String name, long id);

    public abstract BasicDataCenterInfoBuilder newBasicDataCenterInfo();

    public abstract AwsDataCenterInfoBuilder newAwsDataCenterInfo();

    public abstract NetworkAddressBuilder newNetworkAddress();

    public abstract DeltaBuilder newDelta();

    public abstract InstanceInfoBuilder newInstanceInfo();

    public abstract ServicePort newServicePort(int port, boolean secure);

    public abstract ServicePort newServicePort(String name, int port, boolean secure);

    public abstract ServicePort newServicePort(String name, int port, boolean secure, Set<String> labels);

    public static InstanceModel getDefaultModel() {
        return defaultModel;
    }

    public static InstanceModel setDefaultModel(InstanceModel newModel) {
        InstanceModel previous = defaultModel;
        defaultModel = newModel;
        return previous;
    }
}
