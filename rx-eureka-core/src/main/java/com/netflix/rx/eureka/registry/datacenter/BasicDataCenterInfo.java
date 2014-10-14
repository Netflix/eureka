/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.rx.eureka.registry.datacenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.netflix.rx.eureka.registry.DataCenterInfo;
import com.netflix.rx.eureka.registry.NetworkAddress;
import com.netflix.rx.eureka.registry.NetworkAddress.ProtocolType;
import com.netflix.rx.eureka.utils.SystemUtil;

/**
 * This class encapsulates basic server information. It can be created explicitly
 * with {@link com.netflix.rx.eureka.registry.datacenter.BasicDataCenterInfo.Builder} or deduced from the local server
 * system data.
 *
 * @author Tomasz Bak
 */
public class BasicDataCenterInfo extends DataCenterInfo {
    private final String name;
    private final List<NetworkAddress> addresses;

    private BasicDataCenterInfo() {
        name = null;
        addresses = null;
    }

    public BasicDataCenterInfo(String name, List<NetworkAddress> addresses) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BasicDataCenterInfo that = (BasicDataCenterInfo) o;

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

    public static BasicDataCenterInfo fromSystemData() {
        Builder<BasicDataCenterInfo> builder = new Builder<>();
        builder.withName(SystemUtil.getHostName());
        for (String ip : SystemUtil.getLocalIPs()) {
            if (!SystemUtil.isLoopbackIP(ip)) {
                boolean isPublic = SystemUtil.isPublic(ip);
                ProtocolType protocol = SystemUtil.isIPv6(ip) ? ProtocolType.IPv6 : ProtocolType.IPv4;
                builder.withAddresses(new NetworkAddress(protocol, isPublic, ip, null));
            }
        }
        return builder.build();
    }

    public static class Builder<T extends BasicDataCenterInfo> extends DataCenterInfoBuilder<T> {

        private String name;
        private final List<NetworkAddress> addresses = new ArrayList<>();

        public Builder<T> withName(String name) {
            this.name = name;
            return this;
        }

        public Builder<T> withAddresses(NetworkAddress... addresses) {
            this.addresses.addAll(Arrays.asList(addresses));
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T build() {
            return (T) new BasicDataCenterInfo(name, addresses);
        }
    }
}
