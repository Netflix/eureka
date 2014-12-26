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

package com.netflix.eureka2.registry.dsl;

import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.utils.SystemUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class provides simple query DLS to filter a collection of
 * {@link com.netflix.eureka2.registry.instance.NetworkAddress} objects.
 *
 * @author Tomasz Bak
 */
public final class AddressSelector extends DataSelector<NetworkAddress, AddressSelector> {

    private AddressSelector() {
    }

    public static AddressSelector selectBy() {
        return new AddressSelector();
    }

    public AddressSelector label(String... labels) {
        current.add(new LabelCriteria(labels));
        return this;
    }

    public AddressSelector protocolType(NetworkAddress.ProtocolType... protocolTypes) {
        current.add(new ProtocolTypeCriteria(protocolTypes));
        return this;
    }

    public AddressSelector publicIp(boolean publicAddress) {
        current.add(new PublicIpCriteria(publicAddress));
        return this;
    }

    public NetworkAddress returnAddress(List<NetworkAddress> addresses) {
        Iterator<NetworkAddress> it = applyQuery(addresses.iterator());
        return it.hasNext() ? it.next() : null;
    }

    public List<NetworkAddress> returnAddressList(List<NetworkAddress> addresses) {
        Iterator<NetworkAddress> it = applyQuery(addresses.iterator());
        List<NetworkAddress> addressList = new ArrayList<>();
        while (it.hasNext()) {
            addressList.add(it.next());
        }
        return addressList;
    }

    public String returnNameOrIp(List<NetworkAddress> addresses) {
        Iterator<NetworkAddress> it = applyQuery(addresses.iterator());
        if (it.hasNext()) {
            NetworkAddress address = it.next();
            return address.getHostName() != null ? address.getHostName() : address.getIpAddress();
        }
        return null;
    }

    public List<String> returnNameOrIpList(List<NetworkAddress> addresses) {
        Iterator<NetworkAddress> it = applyQuery(addresses.iterator());
        List<String> addressList = new ArrayList<>();
        while (it.hasNext()) {
            NetworkAddress address = it.next();
            addressList.add(address.getHostName() != null ? address.getHostName() : address.getIpAddress());
        }
        return addressList;
    }

    static class LabelCriteria extends Criteria<NetworkAddress, String> {
        LabelCriteria(String... labels) {
            super(labels);
        }

        @Override
        protected boolean matches(String value, NetworkAddress endpoint) {
            return value.equals(endpoint.getLabel());
        }
    }

    static class ProtocolTypeCriteria extends Criteria<NetworkAddress, NetworkAddress.ProtocolType> {
        ProtocolTypeCriteria(NetworkAddress.ProtocolType... protocolTypes) {
            super(protocolTypes);
        }

        @Override
        protected boolean matches(NetworkAddress.ProtocolType value, NetworkAddress endpoint) {
            return endpoint.getProtocolType() == value;
        }
    }

    static class PublicIpCriteria extends Criteria<NetworkAddress, Boolean> {
        PublicIpCriteria(boolean publicAddress) {
            super(publicAddress);
        }

        @Override
        protected boolean matches(Boolean value, NetworkAddress endpoint) {
            String ipAddress = endpoint.getIpAddress();
            if (ipAddress == null) {
                return false;
            }
            return SystemUtil.isPublic(ipAddress) == value;
        }
    }
}
