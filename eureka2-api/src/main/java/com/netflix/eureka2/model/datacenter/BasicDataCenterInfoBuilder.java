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
import java.util.Arrays;
import java.util.List;

import com.netflix.eureka2.model.instance.NetworkAddress;

/**
 */
public abstract class BasicDataCenterInfoBuilder extends DataCenterInfoBuilder<BasicDataCenterInfo> {

    protected String name;
    protected final List<NetworkAddress> addresses = new ArrayList<>();

    public BasicDataCenterInfoBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public BasicDataCenterInfoBuilder withAddresses(NetworkAddress... addresses) {
        this.addresses.addAll(Arrays.asList(addresses));
        return this;
    }
}
