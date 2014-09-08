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

package com.netflix.eureka.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.netflix.eureka.registry.NetworkAddress.ProtocolType;

/**
 * This class represents a location of a server in AWS datacenter.
 *
 * @author Tomasz Bak
 */
public class AwsDataCenterInfo extends DataCenterInfo {

    private final String region;
    private final String zone;
    private final String placementGroup;
    private final String amiId;
    private final String instanceId;
    private final String instanceType;
    private final List<NetworkAddress> addresses;

    // For object creation via reflection.
    private AwsDataCenterInfo() {
        region = zone = placementGroup = amiId = instanceId = instanceType = null;
        addresses = null;
    }

    private AwsDataCenterInfo(AwsDataCenterInfoBuilder builder) {
        region = builder.region;
        zone = builder.zone;
        placementGroup = builder.placementGroup;
        amiId = builder.amiId;
        instanceId = builder.instanceId;
        instanceType = builder.instanceType;
        addresses = new ArrayList<NetworkAddress>(builder.addresses);
    }

    public String getRegion() {
        return region;
    }

    public String getZone() {
        return zone;
    }

    public String getPlacementGroup() {
        return placementGroup;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public List<NetworkAddress> getAddresses() {
        return addresses;
    }

    public String getAmiId() {
        return amiId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getInstanceType() {
        return instanceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AwsDataCenterInfo that = (AwsDataCenterInfo) o;

        if (addresses != null ? !addresses.equals(that.addresses) : that.addresses != null) {
            return false;
        }
        if (amiId != null ? !amiId.equals(that.amiId) : that.amiId != null) {
            return false;
        }
        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) {
            return false;
        }
        if (instanceType != null ? !instanceType.equals(that.instanceType) : that.instanceType != null) {
            return false;
        }
        if (placementGroup != null ? !placementGroup.equals(that.placementGroup) : that.placementGroup != null) {
            return false;
        }
        if (region != null ? !region.equals(that.region) : that.region != null) {
            return false;
        }
        if (zone != null ? !zone.equals(that.zone) : that.zone != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = region != null ? region.hashCode() : 0;
        result = 31 * result + (zone != null ? zone.hashCode() : 0);
        result = 31 * result + (placementGroup != null ? placementGroup.hashCode() : 0);
        result = 31 * result + (amiId != null ? amiId.hashCode() : 0);
        result = 31 * result + (instanceId != null ? instanceId.hashCode() : 0);
        result = 31 * result + (instanceType != null ? instanceType.hashCode() : 0);
        result = 31 * result + (addresses != null ? addresses.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AwsDataCenterInfo{" +
                "region='" + region + '\'' +
                ", zone='" + zone + '\'' +
                ", placementGroup='" + placementGroup + '\'' +
                ", amiId='" + amiId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", instanceType='" + instanceType + '\'' +
                ", addresses=" + addresses +
                '}';
    }

    public static final class AwsDataCenterInfoBuilder extends DataCenterInfoBuilder<AwsDataCenterInfo> {
        private String region;
        private String zone;
        private String placementGroup;
        public String amiId;
        public String instanceId;
        public String instanceType;
        public Set<NetworkAddress> addresses = new HashSet<NetworkAddress>();
        private String privateIP;
        private String privateHostName;
        private String publicIP;
        private String publicHostName;

        public AwsDataCenterInfoBuilder withRegion(String region) {
            this.region = region;
            return this;
        }

        public AwsDataCenterInfoBuilder withZone(String zone) {
            this.zone = zone;
            return this;
        }

        public AwsDataCenterInfoBuilder withPlacementGroup(String placementGroup) {
            this.placementGroup = placementGroup;
            return this;
        }

        public AwsDataCenterInfoBuilder withAmiId(String amiId) {
            this.amiId = amiId;
            return this;
        }

        public AwsDataCenterInfoBuilder withInstanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public AwsDataCenterInfoBuilder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public AwsDataCenterInfoBuilder withAddresses(NetworkAddress... addresses) {
            Collections.addAll(this.addresses, addresses);
            return this;
        }

        public AwsDataCenterInfoBuilder withPrivateIPv4(String privateIP) {
            this.privateIP = privateIP;
            return this;
        }

        public AwsDataCenterInfoBuilder withPrivateHostName(String privateHostName) {
            this.privateHostName = privateHostName;
            return this;
        }

        public AwsDataCenterInfoBuilder withPublicIPv4(String publicIP) {
            this.publicIP = publicIP;
            return this;
        }

        public AwsDataCenterInfoBuilder withPublicHostName(String publicHostName) {
            this.publicHostName = publicHostName;
            return this;
        }

        @Override
        public AwsDataCenterInfo build() {
            if(privateIP != null || privateHostName != null) {
                addresses.add(new NetworkAddress(ProtocolType.IPv4, false, privateIP, privateHostName));
            }
            if(publicIP != null || publicHostName != null) {
                addresses.add(new NetworkAddress(ProtocolType.IPv4, true, publicIP, publicHostName));
            }

            if(region == null && zone != null) { // We will take it from zone name
                region = zone.substring(0, zone.length() - 1);
            }

            return new AwsDataCenterInfo(this);
        }
    }
}
