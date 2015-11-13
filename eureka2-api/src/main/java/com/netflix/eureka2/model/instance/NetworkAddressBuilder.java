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

package com.netflix.eureka2.model.instance;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.NetworkAddress.ProtocolType;

/**
 */
public abstract class NetworkAddressBuilder {

    protected ProtocolType protocolType;
    protected String label;
    protected String ipAddress;
    protected String hostName;

    public static NetworkAddressBuilder aNetworkAddress() {
        return InstanceModel.getDefaultModel().newNetworkAddress();
    }

    public NetworkAddressBuilder withProtocolType(ProtocolType protocolType) {
        this.protocolType = protocolType;
        return this;
    }

    public NetworkAddressBuilder withLabel(String label) {
        this.label = label;
        return this;
    }

    public NetworkAddressBuilder withIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public NetworkAddressBuilder withHostName(String hostName) {
        this.hostName = hostName;
        return this;
    }

    public abstract NetworkAddress build();
}
