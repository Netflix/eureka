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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.netflix.eureka2.model.instance.NetworkAddress;

import static com.netflix.eureka2.model.instance.StdNetworkAddress.PUBLIC_ADDRESS;

/**
 * This class encapsulates basic server information. It can be created explicitly
 * with {@link Builder} or deduced from the local server system data.
 *
 * @author Tomasz Bak
 */
public class StdBasicDataCenterInfo implements BasicDataCenterInfo {
    private final String name;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    private final List<NetworkAddress> addresses;

    @JsonIgnore
    private volatile NetworkAddress defaultAddress;

    private StdBasicDataCenterInfo() {
        name = null;
        addresses = null;
    }

    public StdBasicDataCenterInfo(String name, List<NetworkAddress> addresses) {
        this.name = name;
        this.addresses = addresses;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<NetworkAddress> getAddresses() {
        return addresses;
    }

    /**
     * The order of selection: first public, next private.
     * If there are multiple addresses within a group (for example multiple public IPs), the first in
     * the list is be taken.
     */
    @Override
    public NetworkAddress getDefaultAddress() {
        if (defaultAddress != null) {
            return defaultAddress;
        }
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        NetworkAddress best = null;
        for (NetworkAddress address : addresses) {
            if (best == null) {
                best = address;
                if (best.hasLabel(PUBLIC_ADDRESS)) {
                    break;
                }
            } else if (address.hasLabel(PUBLIC_ADDRESS)) {
                best = address;
                break;
            }
        }
        defaultAddress = best;
        return defaultAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StdBasicDataCenterInfo that = (StdBasicDataCenterInfo) o;

        if (addresses != null ? !addresses.equals(that.addresses) : that.addresses != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (addresses != null ? addresses.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BasicDataCenterInfo{name='" + name + '\'' + ", addresses=" + addresses + '}';
    }

    public static class Builder<T extends StdBasicDataCenterInfo> extends BasicDataCenterInfoBuilder {
        @SuppressWarnings("unchecked")
        @Override
        public T build() {
            return (T) new StdBasicDataCenterInfo(name, addresses);
        }
    }
}
